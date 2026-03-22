package com.poc.temporal.lending.domain.enums

enum class WorkflowEventType {
    /** Loan record saved and Temporal workflow started */
    LOAN_CREATED,

    /** Principal disbursed to borrower; loan transitions to ACTIVE */
    DISBURSED,

    /** Borrower payment applied; status unchanged (workflow decides next state) */
    PAYMENT_RECEIVED,

    /** Payment missed; loan marked DELINQUENT */
    DELINQUENT,

    /** Borrower paid during grace period; loan reinstated to ACTIVE */
    REINSTATED,

    /** All obligations settled; loan marked PAID_OFF */
    PAID_OFF,

    /** Post-payoff cooldown period has begun */
    COOLDOWN_STARTED,

    /** Cooldown period has ended; loan reaches final PAID_OFF state */
    COOLDOWN_ENDED,

    /** Loan cancelled before disbursement */
    CANCELLED
}
