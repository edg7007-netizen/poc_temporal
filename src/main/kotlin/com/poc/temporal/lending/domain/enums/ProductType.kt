package com.poc.temporal.lending.domain.enums

/**
 * Defines the type of lending product.
 *
 * TERM_LOAN  - Fixed monthly installments, daily interest accrual, single withdrawal.
 * BULLET_LOAN - Single payment at maturity, fixed upfront interest, single withdrawal.
 */
enum class ProductType {
    /** Fixed monthly installments, daily compounding interest */
    TERM_LOAN,

    /** Single bullet payment at maturity, interest charged upfront */
    BULLET_LOAN
}
