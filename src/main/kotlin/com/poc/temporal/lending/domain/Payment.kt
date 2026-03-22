package com.poc.temporal.lending.domain

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Records a payment made by the borrower against a loan.
 */
@Entity
@Table(name = "payments")
data class Payment(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    val loan: Loan,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    /** How much of this payment was applied to principal */
    @Column(nullable = false, precision = 19, scale = 2)
    val principalApplied: BigDecimal,

    /** How much of this payment was applied to interest */
    @Column(nullable = false, precision = 19, scale = 2)
    val interestApplied: BigDecimal,

    /** How much of this payment was applied to late fees */
    @Column(nullable = false, precision = 19, scale = 2)
    val lateFeesApplied: BigDecimal,

    @Column(length = 200)
    val referenceNumber: String? = null,

    @Column(nullable = false)
    val paymentDate: LocalDateTime = LocalDateTime.now()
)
