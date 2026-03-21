package com.poc.temporal.lending.domain

import com.poc.temporal.lending.domain.enums.LedgerEntryType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Double-entry style ledger entry for a loan.
 * Each financial event (disbursement, interest, payment, fee) is recorded here.
 */
@Entity
@Table(name = "ledger_entries")
data class LedgerEntry(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    val loan: Loan,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val entryType: LedgerEntryType,

    /** Positive amounts increase the borrower's balance (debit), negative decrease it */
    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    /** Running balance after this entry */
    @Column(nullable = false, precision = 19, scale = 2)
    val runningBalance: BigDecimal,

    @Column(length = 500)
    val description: String? = null,

    /** Reference ID (e.g., payment ID, workflow run ID) */
    @Column
    val referenceId: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
