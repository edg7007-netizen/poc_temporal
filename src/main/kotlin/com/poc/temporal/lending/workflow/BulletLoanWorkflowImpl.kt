package com.poc.temporal.lending.workflow

import io.temporal.spring.boot.WorkflowImpl
import io.temporal.workflow.Workflow
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Manages the lifecycle of a **Bullet Loan**:
 *
 * 1. Disburse principal and charge a flat interest amount upfront.
 * 2. Wait for a single full payment by the maturity date.
 * 3. If maturity passes without payment: mark delinquent, charge a late fee,
 *    and open a 30-day extended grace period.
 * 4. When fully paid off: enter the configured cooldown period before completing.
 *
 * All timing is handled by Temporal timers, making this workflow durable and resumable
 * after server restarts without losing state.
 */
@WorkflowImpl(taskQueues = ["lending-task-queue"])
class BulletLoanWorkflowImpl : AbstractLoanWorkflow(), BulletLoanWorkflow {

    override fun execute(request: LoanWorkflowRequest) {
        logger.info("Starting Bullet Loan workflow for loanId=${request.loanId}")

        disburseWithUpfrontInterest(request)
        waitForPaymentOrHandleMaturityDefault(request)
        finalizeLoan(request)

        logger.info("Bullet Loan workflow completed for loanId=${request.loanId}, status=$currentLoanStatus")
    }

    // ── Step 1: Disbursement with upfront interest ─────────────────────────────

    private fun disburseWithUpfrontInterest(request: LoanWorkflowRequest) {
        loanActivities.disburseLoan(request.loanId)
        ledgerActivities.recordDisbursement(request.loanId, request.principalAmount)
        balance = request.principalAmount
        currentLoanStatus = "ACTIVE"

        val upfrontInterest = calculateUpfrontInterest(request)
        ledgerActivities.recordUpfrontInterest(request.loanId, upfrontInterest)
        balance += upfrontInterest
        logger.info("Upfront interest of $upfrontInterest charged for loanId=${request.loanId}")
    }

    private fun calculateUpfrontInterest(request: LoanWorkflowRequest): BigDecimal {
        val rate = request.flatInterestRate ?: request.annualInterestRate
        return (request.principalAmount * rate).setScale(2, RoundingMode.HALF_UP)
    }

    // ── Step 2: Await full payment by maturity ─────────────────────────────────

    private fun waitForPaymentOrHandleMaturityDefault(request: LoanWorkflowRequest) {
        val maturityWindow = Duration.ofDays(request.numberOfPaymentCycles * 30L)
        val paidByMaturity = awaitPaymentWithinWindow(request.loanId, maturityWindow)

        if (!paidByMaturity && !cancelled && balance > BigDecimal.ZERO) {
            handleMaturityDefault(request.loanId, request.lateFeeRate)
        }
    }

    // ── Step 3: Maturity default handling ─────────────────────────────────────

    private fun handleMaturityDefault(loanId: Long, lateFeeRate: BigDecimal) {
        logger.warn("Maturity reached without payment for loanId=$loanId. Marking delinquent.")
        loanActivities.markDelinquent(loanId)
        currentLoanStatus = "DELINQUENT"

        chargeLateFee(loanId, lateFeeRate)
        awaitPaymentWithinWindow(loanId, Duration.ofDays(30))
    }
}
