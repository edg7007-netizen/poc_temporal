package com.poc.temporal.lending.domain.enums

/**
 * Defines the type of lending product.
 *
 * INSTALLMENTS - Fixed monthly installments, daily interest accrual, single withdrawal.
 * CDD          - Custom Due Date: single payment at maturity, fixed upfront interest, single withdrawal.
 */
enum class ProductType {
    /** Fixed monthly installments, daily compounding interest */
    INSTALLMENTS,

    /** Custom Due Date: single bullet payment at maturity, interest charged upfront */
    CDD
}
