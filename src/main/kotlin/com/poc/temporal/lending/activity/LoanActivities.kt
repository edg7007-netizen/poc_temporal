package com.poc.temporal.lending.activity

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import java.math.BigDecimal

/**
 * Activities that interact with the loan domain (database / business logic).
 */
@ActivityInterface
interface LoanActivities {

    /** Marks the loan as ACTIVE and records disbursement date */
    @ActivityMethod
    fun disburseLoan(loanId: Long): Unit

    /** Applies a payment to the loan; returns remaining balance */
    @ActivityMethod
    fun applyPayment(loanId: Long, amount: BigDecimal, referenceNumber: String): BigDecimal

    /** Marks the loan as DELINQUENT when a payment is missed */
    @ActivityMethod
    fun markDelinquent(loanId: Long): Unit

    /** Marks the loan as PAID_OFF */
    @ActivityMethod
    fun markPaidOff(loanId: Long): Unit

    /** Cancels the loan if it has not been disbursed yet */
    @ActivityMethod
    fun cancelLoan(loanId: Long, reason: String): Unit

    /** Starts the cooldown period; sets status to IN_COOLDOWN */
    @ActivityMethod
    fun startCooldown(loanId: Long): Unit

    /** Ends the cooldown period and transitions loan to final PAID_OFF state */
    @ActivityMethod
    fun endCooldown(loanId: Long): Unit

    /** Updates loan status */
    @ActivityMethod
    fun updateLoanStatus(loanId: Long, status: String): Unit
}
