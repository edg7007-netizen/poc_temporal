package com.poc.temporal.lending.workflow

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Manages the lifecycle of an **Installments** loan:
 *
 * 1. Disburse principal and start daily interest tracking.
 * 2. For each monthly payment cycle, accrue interest daily and watch for a payment signal.
 * 3. If no payment arrives by cycle end: mark delinquent, charge a late fee, and open a
 *    15-day grace period. Reinstate ACTIVE status if the borrower pays during grace.
 * 4. When fully paid off: enter the configured cooldown period before completing.
 *
 * All timing is handled by Temporal timers, making this workflow durable and resumable
 * after server restarts without losing state.
 */
@WorkflowImpl(taskQueues = ["lending-task-queue"])
class InstallmentsWorkflowImpl : AbstractLoanWorkflow(), InstallmentsWorkflow {

    override fun execute(request: LoanWorkflowRequest) {
        logger.info("Starting Installments workflow for loanId=${request.loanId}")

        disburseAndActivateLoan(request)
        runMonthlyPaymentCycles(request)
        finalizeLoan(request)

        logger.info("Installments workflow completed for loanId=${request.loanId}, status=$currentLoanStatus")
    }

    // ── Step 1: Disbursement ───────────────────────────────────────────────────

    private fun disburseAndActivateLoan(request: LoanWorkflowRequest) {
        loanActivities.disburseLoan(request.loanId)
        ledgerActivities.recordDisbursement(request.loanId, request.principalAmount)
        balance = request.principalAmount
        currentLoanStatus = "ACTIVE"
    }

    // ── Step 2: Monthly payment cycles ────────────────────────────────────────

    private fun runMonthlyPaymentCycles(request: LoanWorkflowRequest) {
        val dailyRate = dailyInterestRate(request.annualInterestRate)

        for (cycleNumber in 1..request.numberOfPaymentCycles) {
            if (cancelled || balance <= BigDecimal.ZERO) break
            processSinglePaymentCycle(cycleNumber, request.numberOfPaymentCycles, request.loanId, dailyRate, request.lateFeeRate)
        }
    }

    private fun processSinglePaymentCycle(
        cycleNumber: Int,
        totalCycles: Int,
        loanId: Long,
        dailyRate: BigDecimal,
        lateFeeRate: BigDecimal
    ) {
        logger.info("Starting payment cycle $cycleNumber/$totalCycles for loanId=$loanId")

        val paymentReceivedThisCycle = accrueDailyInterestAndWatchForPayment(loanId, dailyRate)

        if (!paymentReceivedThisCycle && !cancelled && balance > BigDecimal.ZERO) {
            handleMissedCyclePayment(cycleNumber, loanId, lateFeeRate)
        }
    }

    /**
     * Iterates through 30 daily windows within a payment cycle.
     * Each day: accrues one day's interest, sleeps 1 day, then checks for a payment signal.
     * Returns `true` as soon as a payment is received.
     */
    private fun accrueDailyInterestAndWatchForPayment(loanId: Long, dailyRate: BigDecimal): Boolean {
        for (day in 1..30) {
            if (cancelled || balance <= BigDecimal.ZERO) break

            ledgerActivities.recordDailyInterest(loanId, dailyRate)
            balance = ledgerActivities.getOutstandingBalance(loanId)
            Workflow.sleep(Duration.ofDays(1))

            if (pendingPaymentAmount != null) {
                applyPendingPayment(loanId)
                return true
            }
        }
        return false
    }

    // ── Step 3: Missed payment handling ───────────────────────────────────────

    private fun handleMissedCyclePayment(cycleNumber: Int, loanId: Long, lateFeeRate: BigDecimal) {
        logger.warn("No payment received for cycle $cycleNumber, loanId=$loanId. Marking delinquent.")
        loanActivities.markDelinquent(loanId)
        currentLoanStatus = "DELINQUENT"

        chargeLateFee(loanId, lateFeeRate)
        reinstateActiveIfPaidDuringGracePeriod(loanId)
    }

    private fun reinstateActiveIfPaidDuringGracePeriod(loanId: Long) {
        val paidDuringGrace = awaitPaymentWithinWindow(loanId, gracePeriod = Duration.ofDays(15))
        if (paidDuringGrace && currentLoanStatus == "DELINQUENT") {
            loanActivities.updateLoanStatus(loanId, "ACTIVE")
            currentLoanStatus = "ACTIVE"
            logger.info("Loan $loanId reinstated to ACTIVE after grace period payment")
        }
    }

    // ── Private utilities ──────────────────────────────────────────────────────

    private fun dailyInterestRate(annualRate: BigDecimal): BigDecimal =
        annualRate.divide(BigDecimal("365"), 10, RoundingMode.HALF_UP)
}
