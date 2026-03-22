package com.poc.temporal.lending.domain.enums

enum class LoanStatus {
    /** Loan application submitted, pending approval */
    PENDING,

    /** Loan approved but not yet disbursed */
    APPROVED,

    /** Loan has been disbursed to the borrower */
    ACTIVE,

    /** One or more payments are overdue */
    DELINQUENT,

    /** Loan fully repaid */
    PAID_OFF,

    /** In cooldown period after full repayment */
    IN_COOLDOWN,

    /** Loan was cancelled before disbursement */
    CANCELLED
}
