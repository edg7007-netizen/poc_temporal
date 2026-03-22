package com.poc.temporal.lending.workflow

import io.temporal.spring.boot.WorkflowImpl
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Manages the lifecycle of a **CDD (Custom Due Date)** loan:
 *
 * 1. Disburse principal and charge a flat interest amount upfront.
 * 2. Wait for a single full payment by the maturity date.
 * 3. If maturity passes without payment: mark delinquent, charge a late fee,
 *    and open a 30-day extended grace period.
 * 4. When fully paid off: enter the configured cooldown period before completing.
 *
 * Shared state and helpers live in [LoanWorkflowDelegate], composed here rather than
 * inherited, keeping this class focused purely on CDD-specific lifecycle logic.
 */
@WorkflowImpl(taskQueues = ["lending-task-queue"])
class CDDWorkflowImpl : CDDWorkflow {

    private val state = LoanWorkflowDelegate()

    // ── Signal and query delegation ────────────────────────────────────────────

    override fun receivePayment(amount: BigDecimal, referenceNumber: String) =
        state.receivePayment(amount, referenceNumber)

    override fun cancelLoan(reason: String) = state.cancelLoan(reason)
    override fun getLoanStatus(): String = state.getLoanStatus()
    override fun getOutstandingBalance(): BigDecimal = state.getOutstandingBalance()
    override fun isInCooldown(): Boolean = state.isInCooldown()

    // ── Workflow execution ─────────────────────────────────────────────────────

    override fun execute(request: LoanWorkflowRequest) {
        state.logger.info("Starting CDD workflow for loanId=${request.loanId}")

        disburseWithUpfrontInterest(request)
        waitForPaymentOrHandleMaturityDefault(request)
        state.finalizeLoan(request)

        state.logger.info("CDD workflow completed for loanId=${request.loanId}, status=${state.currentLoanStatus}")
    }

    // ── Step 1: Disbursement with upfront interest ─────────────────────────────

    private fun disburseWithUpfrontInterest(request: LoanWorkflowRequest) {
        state.loanActivities.disburseLoan(request.loanId)
        state.ledgerActivities.recordDisbursement(request.loanId, request.principalAmount)
        state.balance = request.principalAmount
        state.currentLoanStatus = "ACTIVE"

        val upfrontInterest = calculateUpfrontInterest(request)
        state.ledgerActivities.recordUpfrontInterest(request.loanId, upfrontInterest)
        state.balance += upfrontInterest
        state.logger.info("Upfront interest of $upfrontInterest charged for loanId=${request.loanId}")
    }

    private fun calculateUpfrontInterest(request: LoanWorkflowRequest): BigDecimal {
        val rate = request.flatInterestRate ?: request.annualInterestRate
        return (request.principalAmount * rate).setScale(2, RoundingMode.HALF_UP)
    }

    // ── Step 2: Await full payment by maturity ─────────────────────────────────

    private fun waitForPaymentOrHandleMaturityDefault(request: LoanWorkflowRequest) {
        val maturityWindow = Duration.ofDays(request.numberOfPaymentCycles * 30L)
        val paidByMaturity = state.awaitPaymentWithinWindow(request.loanId, maturityWindow)

        if (!paidByMaturity && !state.cancelled && state.balance > BigDecimal.ZERO) {
            handleMaturityDefault(request.loanId, request.lateFeeRate)
        }
    }

    // ── Step 3: Maturity default handling ─────────────────────────────────────

    private fun handleMaturityDefault(loanId: Long, lateFeeRate: BigDecimal) {
        state.logger.warn("Maturity reached without payment for loanId=$loanId. Marking delinquent.")
        state.loanActivities.markDelinquent(loanId)
        state.currentLoanStatus = "DELINQUENT"

        state.chargeLateFee(loanId, lateFeeRate)
        state.awaitPaymentWithinWindow(loanId, Duration.ofDays(30))
    }
}
