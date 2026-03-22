package com.poc.temporal.lending.workflow

import io.temporal.workflow.QueryMethod
import io.temporal.workflow.SignalMethod
import java.math.BigDecimal

/**
 * Common signals and queries shared by all loan workflow types.
 *
 * This interface is intentionally NOT annotated with [@io.temporal.workflow.WorkflowInterface]
 * so that product-specific workflow interfaces can extend it and evolve independently without
 * forcing changes across all product types.
 */
interface LoanWorkflow {

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
