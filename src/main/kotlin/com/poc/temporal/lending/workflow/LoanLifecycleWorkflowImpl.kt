package com.poc.temporal.lending.workflow

import com.poc.temporal.lending.activity.LedgerActivities
import com.poc.temporal.lending.activity.LoanActivities
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.ProductType
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Orchestrates the full lifecycle of a loan using Temporal.io.
 *
 * State machine:
 *   PENDING → disburse → ACTIVE
 *     ↓ (signal: payment received)
 *   payment loop until balance = 0 → markPaidOff → startCooldown → wait → endCooldown
 *     ↓ (signal: cancel)
 *   cancelLoan → CANCELLED  (only valid before disbursement)
 *
 * For TERM_LOAN:
 *   - Interest accrues daily via a child-like timer loop.
 *   - One payment per cycle expected; late fees charged if overdue.
 *
 * For BULLET_LOAN:
 *   - Interest charged upfront (flat rate × principal).
 *   - Single payment expected at maturity.
 */
@WorkflowImpl(taskQueues = ["lending-task-queue"])
class LoanLifecycleWorkflowImpl : LoanLifecycleWorkflow {

    private val logger = Workflow.getLogger(LoanLifecycleWorkflowImpl::class.java)

    private val activityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setRetryOptions(
            RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .build()
        )
        .build()

    private val loanActivities = Workflow.newActivityStub(LoanActivities::class.java, activityOptions)
    private val ledgerActivities = Workflow.newActivityStub(LedgerActivities::class.java, activityOptions)

    // Mutable workflow state (safe in Temporal's single-threaded model)
    private var outstandingBalance: BigDecimal = BigDecimal.ZERO
    private var cancelled = false
    private var cancellationReason: String = ""
    private var paymentReceived: BigDecimal? = null
    private var paymentReference: String = ""
    private var loanStatus: String = "PENDING"
    private var inCooldown: Boolean = false

    override fun execute(request: LoanWorkflowRequest) {
        logger.info("Starting loan lifecycle workflow for loanId=${request.loanId}")

        // Step 1: Disburse the loan
        loanActivities.disburseLoan(request.loanId)
        ledgerActivities.recordDisbursement(request.loanId, request.principalAmount)
        outstandingBalance = request.principalAmount
        loanStatus = "ACTIVE"

        // Step 2: Handle upfront interest for BULLET_LOAN
        if (request.interestAccrualMethod == InterestAccrualMethod.FIXED_UPFRONT) {
            val flatRate = request.flatInterestRate ?: request.annualInterestRate
            val upfrontInterest = (request.principalAmount * flatRate).setScale(2, RoundingMode.HALF_UP)
            ledgerActivities.recordUpfrontInterest(request.loanId, upfrontInterest)
            outstandingBalance += upfrontInterest
        }

        // Step 3: Run loan lifecycle
        when (request.productType) {
            ProductType.TERM_LOAN -> runTermLoanLifecycle(request)
            ProductType.BULLET_LOAN -> runBulletLoanLifecycle(request)
        }

        logger.info("Loan lifecycle workflow completed for loanId=${request.loanId}, status=$loanStatus")
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TERM LOAN: monthly cycles with daily interest accrual
    // ──────────────────────────────────────────────────────────────────────────
    private fun runTermLoanLifecycle(request: LoanWorkflowRequest) {
        val dailyRate = request.annualInterestRate.divide(
            BigDecimal("365"), 10, RoundingMode.HALF_UP
        )
        val cycleDurationSeconds = Duration.ofDays(30).seconds
        val oneDaySeconds = Duration.ofDays(1).seconds

        for (cycle in 1..request.numberOfPaymentCycles) {
            if (cancelled) break
            if (outstandingBalance <= BigDecimal.ZERO) break

            logger.info("Starting payment cycle $cycle/${request.numberOfPaymentCycles} for loanId=${request.loanId}")

            // Accrue interest daily within this cycle (30 days simulated)
            var dayInCycle = 0
            var paymentReceivedForCycle = false

            // Wait up to 30 days, accruing interest each day and watching for payment
            repeat(30) { day ->
                if (cancelled || paymentReceivedForCycle || outstandingBalance <= BigDecimal.ZERO) return@repeat

                // Accrue one day's interest
                ledgerActivities.recordDailyInterest(request.loanId, dailyRate)
                outstandingBalance = ledgerActivities.getOutstandingBalance(request.loanId)
                dayInCycle++

                // Wait 1 day (uses Temporal timers - skippable in tests)
                Workflow.sleep(Duration.ofSeconds(oneDaySeconds))

                // Check for signal after sleeping
                if (paymentReceived != null) {
                    val payment = paymentReceived!!
                    val ref = paymentReference
                    paymentReceived = null
                    paymentReference = ""
                    processPayment(request.loanId, payment, ref)
                    paymentReceivedForCycle = true
                }
            }

            // If no payment received by end of cycle, mark delinquent and charge late fee
            if (!paymentReceivedForCycle && !cancelled && outstandingBalance > BigDecimal.ZERO) {
                logger.warn("No payment received for cycle $cycle, loanId=${request.loanId}. Marking delinquent.")
                loanActivities.markDelinquent(request.loanId)
                loanStatus = "DELINQUENT"

                // Charge late fee on outstanding balance
                ledgerActivities.recordLateFee(request.loanId, outstandingBalance, request.lateFeeRate)
                outstandingBalance = ledgerActivities.getOutstandingBalance(request.loanId)

                // Grace period: wait another 15 days for the payment
                val paid = waitForPaymentWithGrace(request.loanId, Duration.ofDays(15))
                if (paid && loanStatus == "DELINQUENT") {
                    loanActivities.updateLoanStatus(request.loanId, "ACTIVE")
                    loanStatus = "ACTIVE"
                }
            }
        }

        finalizeLoan(request)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BULLET LOAN: single payment at maturity
    // ──────────────────────────────────────────────────────────────────────────
    private fun runBulletLoanLifecycle(request: LoanWorkflowRequest) {
        val maturityDays = request.numberOfPaymentCycles * 30L

        // Wait for payment signal or maturity
        val paid = waitForPaymentWithGrace(request.loanId, Duration.ofDays(maturityDays))

        if (!paid && !cancelled && outstandingBalance > BigDecimal.ZERO) {
            // Maturity passed without payment: delinquent + late fee
            loanActivities.markDelinquent(request.loanId)
            loanStatus = "DELINQUENT"
            ledgerActivities.recordLateFee(request.loanId, outstandingBalance, request.lateFeeRate)
            outstandingBalance = ledgerActivities.getOutstandingBalance(request.loanId)

            // Extended grace period
            waitForPaymentWithGrace(request.loanId, Duration.ofDays(30))
        }

        finalizeLoan(request)
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Waits up to [gracePeriod] for a payment signal; returns true if payment received */
    private fun waitForPaymentWithGrace(loanId: Long, gracePeriod: Duration): Boolean {
        val gotPayment = Workflow.await(gracePeriod) { paymentReceived != null || cancelled }
        if (paymentReceived != null) {
            val payment = paymentReceived!!
            val ref = paymentReference
            paymentReceived = null
            paymentReference = ""
            processPayment(loanId, payment, ref)
            return true
        }
        return false
    }

    private fun processPayment(loanId: Long, amount: BigDecimal, ref: String) {
        ledgerActivities.recordPayment(loanId, amount, ref)
        outstandingBalance = loanActivities.applyPayment(loanId, amount, ref)
        logger.info("Payment of $amount applied to loanId=$loanId, remaining balance=$outstandingBalance")
    }

    private fun finalizeLoan(request: LoanWorkflowRequest) {
        if (cancelled) {
            loanActivities.cancelLoan(request.loanId, cancellationReason)
            loanStatus = "CANCELLED"
            return
        }

        if (outstandingBalance <= BigDecimal.ZERO) {
            loanActivities.markPaidOff(request.loanId)
            loanStatus = "PAID_OFF"

            // Start cooldown period
            loanActivities.startCooldown(request.loanId)
            loanStatus = "IN_COOLDOWN"
            inCooldown = true

            Workflow.sleep(Duration.ofDays(request.cooldownPeriodDays.toLong()))

            loanActivities.endCooldown(request.loanId)
            loanStatus = "PAID_OFF"
            inCooldown = false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Signals
    // ──────────────────────────────────────────────────────────────────────────

    override fun receivePayment(amount: BigDecimal, referenceNumber: String) {
        paymentReceived = amount
        paymentReference = referenceNumber
    }

    override fun cancelLoan(reason: String) {
        if (loanStatus == "PENDING" || loanStatus == "APPROVED") {
            cancelled = true
            cancellationReason = reason
        } else {
            logger.warn("Cancel signal ignored: loan is already in status $loanStatus")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Queries
    // ──────────────────────────────────────────────────────────────────────────

    override fun getLoanStatus(): String = loanStatus

    override fun getOutstandingBalance(): BigDecimal = outstandingBalance

    override fun isInCooldown(): Boolean = inCooldown
}
