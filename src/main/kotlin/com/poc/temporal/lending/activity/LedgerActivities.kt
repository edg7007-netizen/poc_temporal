package com.poc.temporal.lending.activity

import io.temporal.activity.ActivityInterface
import io.temporal.activity.ActivityMethod
import java.math.BigDecimal

/**
 * Activities for maintaining the loan ledger.
 */
@ActivityInterface
interface LedgerActivities {

    /** Records a disbursement entry in the ledger */
    @ActivityMethod
    fun recordDisbursement(loanId: Long, amount: BigDecimal): Unit

    /** Records an upfront fixed-interest entry */
    @ActivityMethod
    fun recordUpfrontInterest(loanId: Long, amount: BigDecimal): Unit

    /** Records one day's interest accrual */
    @ActivityMethod
    fun recordDailyInterest(loanId: Long, dailyRate: BigDecimal): Unit

    /** Records a late fee accrual */
    @ActivityMethod
    fun recordLateFee(loanId: Long, overdueAmount: BigDecimal, lateFeeRate: BigDecimal): Unit

    /** Records a payment in the ledger */
    @ActivityMethod
    fun recordPayment(loanId: Long, amount: BigDecimal, referenceId: String?): Unit

    /** Returns the current outstanding balance */
    @ActivityMethod
    fun getOutstandingBalance(loanId: Long): BigDecimal
}
