package com.poc.temporal.lending.domain.enums

enum class LedgerEntryType {
    /** Principal disbursement to borrower */
    DISBURSEMENT,

    /** Daily or upfront interest charge */
    INTEREST_ACCRUAL,

    /** Late payment fee */
    LATE_FEE,

    /** Borrower payment applied to the loan */
    PAYMENT,

    /** Upfront fee charged at origination */
    ORIGINATION_FEE,

    /** Reversal or adjustment entry */
    ADJUSTMENT
}
