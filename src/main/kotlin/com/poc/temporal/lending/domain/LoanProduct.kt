package com.poc.temporal.lending.domain

import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.ProductType
import jakarta.persistence.*
import java.math.BigDecimal

/**
 * Represents a lending product template. All loans are created from a product.
 *
 * Two representative products are pre-seeded:
 *  1. Term Loan  – monthly installments, daily accrual, single withdrawal.
 *  2. Bullet Loan – single payment at maturity, fixed upfront interest.
 */
@Entity
@Table(name = "loan_products")
data class LoanProduct(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val productType: ProductType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val interestAccrualMethod: InterestAccrualMethod,

    /** Annual interest rate expressed as a decimal, e.g. 0.18 for 18% */
    @Column(nullable = false, precision = 8, scale = 6)
    val annualInterestRate: BigDecimal,

    /** For BULLET_LOAN: the flat interest rate applied to principal upfront */
    @Column(precision = 8, scale = 6)
    val flatInterestRate: BigDecimal? = null,

    /** Number of payment cycles (months). 1 for bullet loans. */
    @Column(nullable = false)
    val numberOfPaymentCycles: Int,

    /** Whether the borrower may make more than one withdrawal from the credit line */
    @Column(nullable = false)
    val allowsMultipleWithdrawals: Boolean = false,

    /** Maximum loan amount that can be granted under this product */
    @Column(nullable = false, precision = 19, scale = 2)
    val maxLoanAmount: BigDecimal,

    /** Minimum loan amount */
    @Column(nullable = false, precision = 19, scale = 2)
    val minLoanAmount: BigDecimal,

    /** Cooldown period in days after loan is fully repaid */
    @Column(nullable = false)
    val cooldownPeriodDays: Int = 30,

    /** Late-fee charged as a percentage of the overdue amount (e.g. 0.05 = 5%) */
    @Column(nullable = false, precision = 8, scale = 6)
    val lateFeeRate: BigDecimal = BigDecimal("0.05"),

    @Column(nullable = false)
    val active: Boolean = true
)
