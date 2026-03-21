package com.poc.temporal.lending.workflow

import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.math.BigDecimal

/**
 * Manages the complete lifecycle of a single loan from disbursement to payoff.
 *
 * Signals allow external parties (API, manual ops) to interact with the running workflow.
 * Queries expose the current loan state without modifying it.
 */
@WorkflowInterface
interface LoanLifecycleWorkflow {

    @WorkflowMethod
    fun execute(request: LoanWorkflowRequest)

    /** Signals a payment received from the borrower */
    @SignalMethod
    fun receivePayment(amount: BigDecimal, referenceNumber: String)

    /** Manually cancel the loan (only valid before disbursement) */
    @SignalMethod
    fun cancelLoan(reason: String)

    /** Query the current loan status */
    @QueryMethod
    fun getLoanStatus(): String

    /** Query the current outstanding balance */
    @QueryMethod
    fun getOutstandingBalance(): BigDecimal

    /** Query whether the loan is currently in cooldown */
    @QueryMethod
    fun isInCooldown(): Boolean
}
