package com.poc.temporal.lending.domain

import com.poc.temporal.lending.domain.enums.LoanStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Represents a single loan instance in its full lifecycle.
 */
@Entity
@Table(name = "loans")
data class Loan(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    val product: LoanProduct,

    /** External borrower identifier */
    @Column(nullable = false)
    val borrowerId: String,

    /** Requested/approved principal amount */
    @Column(nullable = false, precision = 19, scale = 2)
    val principalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: LoanStatus = LoanStatus.PENDING,

    /** ID of the Temporal workflow managing this loan's lifecycle */
    @Column(unique = true)
    var workflowId: String? = null,

    /** Date the loan was approved */
    val approvalDate: LocalDate? = null,

    /** Date the loan was disbursed */
    var disbursementDate: LocalDate? = null,

    /** Scheduled maturity date */
    var maturityDate: LocalDate? = null,

    /** Outstanding balance (principal + accrued interest - payments) */
    @Column(nullable = false, precision = 19, scale = 2)
    var outstandingBalance: BigDecimal = BigDecimal.ZERO,

    /** Total accrued interest to date */
    @Column(nullable = false, precision = 19, scale = 2)
    var accruedInterest: BigDecimal = BigDecimal.ZERO,

    /** Total late fees accrued */
    @Column(nullable = false, precision = 19, scale = 2)
    var lateFees: BigDecimal = BigDecimal.ZERO,

    /** Total amount paid by borrower */
    @Column(nullable = false, precision = 19, scale = 2)
    var totalPaid: BigDecimal = BigDecimal.ZERO,

    /** Number of payments made */
    @Column(nullable = false)
    var paymentsMade: Int = 0,

    /** Date when cooldown period ends (set when loan is paid off) */
    var cooldownEndDate: LocalDate? = null,

    /** Notes or reason for manual operations */
    @Column(length = 1000)
    var notes: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
