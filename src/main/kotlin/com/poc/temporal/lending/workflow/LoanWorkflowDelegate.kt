package com.poc.temporal.lending.workflow

import com.poc.temporal.lending.activity.LedgerActivities
import com.poc.temporal.lending.activity.LoanActivities
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.Workflow
import java.math.BigDecimal
import java.time.Duration

/**
 * Holds all mutable workflow state, activity stubs, signal/query implementations, and shared
 * lifecycle helpers that are identical for every product type.
 *
 * Used via **composition** inside each concrete workflow class — [InstallmentsWorkflowImpl] and
 * [CDDWorkflowImpl] each create one instance and delegate to it, replacing the previous abstract-
 * class approach with a plain object that has no coupling to any workflow hierarchy.
 *
 * Must be instantiated within a Temporal workflow thread (i.e. as a field of the workflow class)
 * so that [Workflow.newActivityStub] and [Workflow.getLogger] execute in the correct context.
 */
class LoanWorkflowDelegate {

    val logger = Workflow.getLogger(LoanWorkflowDelegate::class.java)

    private val activityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
        .build()

    val loanActivities: LoanActivities =
        Workflow.newActivityStub(LoanActivities::class.java, activityOptions)

    val ledgerActivities: LedgerActivities =
        Workflow.newActivityStub(LedgerActivities::class.java, activityOptions)

    // ── Mutable workflow state ─────────────────────────────────────────────────

    var balance: BigDecimal = BigDecimal.ZERO
    var currentLoanStatus: String = "PENDING"
    var cooldownActive: Boolean = false
    var cancelled: Boolean = false
    var cancellationReason: String = ""
    var pendingPaymentAmount: BigDecimal? = null
    var pendingPaymentReference: String = ""

    // ── Signal handlers ────────────────────────────────────────────────────────

    fun receivePayment(amount: BigDecimal, referenceNumber: String) {
        pendingPaymentAmount = amount
        pendingPaymentReference = referenceNumber
    }

    fun cancelLoan(reason: String) {
        if (currentLoanStatus == "PENDING" || currentLoanStatus == "APPROVED") {
            cancelled = true
            cancellationReason = reason
        } else {
            logger.warn("Cancel signal ignored: loan is already in status $currentLoanStatus")
        }
    }

    // ── Query handlers ─────────────────────────────────────────────────────────

    fun getLoanStatus(): String = currentLoanStatus
    fun getOutstandingBalance(): BigDecimal = balance
    fun isInCooldown(): Boolean = cooldownActive

    // ── Shared workflow helpers ────────────────────────────────────────────────

    /**
     * Waits up to [gracePeriod] for a payment signal to arrive.
     * Consumes and applies the payment if received.
     * Returns `true` if a payment was processed, `false` if the window elapsed without payment.
     */
    fun awaitPaymentWithinWindow(loanId: Long, gracePeriod: Duration): Boolean {
        Workflow.await(gracePeriod) { pendingPaymentAmount != null || cancelled }
        return if (pendingPaymentAmount != null) {
            applyPendingPayment(loanId)
            true
        } else {
            false
        }
    }

    /**
     * Consumes the pending payment signal, posts it to the ledger, and updates the outstanding balance.
     */
    fun applyPendingPayment(loanId: Long) {
        val amount = pendingPaymentAmount!!
        val reference = pendingPaymentReference
        pendingPaymentAmount = null
        pendingPaymentReference = ""
        ledgerActivities.recordPayment(loanId, amount, reference)
        balance = loanActivities.applyPayment(loanId, amount, reference)
        logger.info("Payment of $amount applied to loanId=$loanId, remaining balance=$balance")
    }

    /**
     * Charges a late fee on the outstanding balance and refreshes [balance].
     */
    fun chargeLateFee(loanId: Long, lateFeeRate: BigDecimal) {
        ledgerActivities.recordLateFee(loanId, balance, lateFeeRate)
        balance = ledgerActivities.getOutstandingBalance(loanId)
    }

    /**
     * Marks the loan as CANCELLED or triggers the paid-off + cooldown sequence.
     */
    fun finalizeLoan(request: LoanWorkflowRequest) {
        if (cancelled) {
            loanActivities.cancelLoan(request.loanId, cancellationReason)
            currentLoanStatus = "CANCELLED"
            logger.info("Loan ${request.loanId} cancelled: $cancellationReason")
            return
        }

        if (balance <= BigDecimal.ZERO) {
            markLoanAsPaidOff(request.loanId)
            enforceCooldownPeriod(request.loanId, request.cooldownPeriodDays)
        }
    }

    private fun markLoanAsPaidOff(loanId: Long) {
        loanActivities.markPaidOff(loanId)
        currentLoanStatus = "PAID_OFF"
        logger.info("Loan $loanId fully paid off")
    }

    private fun enforceCooldownPeriod(loanId: Long, cooldownDays: Int) {
        loanActivities.startCooldown(loanId)
        currentLoanStatus = "IN_COOLDOWN"
        cooldownActive = true
        logger.info("Loan $loanId entering $cooldownDays-day cooldown period")

        Workflow.sleep(Duration.ofDays(cooldownDays.toLong()))

        loanActivities.endCooldown(loanId)
        currentLoanStatus = "PAID_OFF"
        cooldownActive = false
        logger.info("Loan $loanId cooldown complete")
    }
}
