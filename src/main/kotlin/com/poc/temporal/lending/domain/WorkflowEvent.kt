package com.poc.temporal.lending.domain

import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.domain.enums.WorkflowEventType
import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * Records every significant state transition driven by a Temporal workflow activity.
 *
 * Together with [LedgerEntry] and [Payment] this table gives a complete, human-readable
 * audit trail of what the Temporal workflow did to a loan — useful for debugging,
 * monitoring, and demonstration purposes.
 */
@Entity
@Table(name = "workflow_events")
data class WorkflowEvent(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id", nullable = false)
    val loan: Loan,

    /** The Temporal workflow ID driving this transition (mirrors [Loan.workflowId]) */
    @Column
    val workflowId: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val eventType: WorkflowEventType,

    /** Loan status before this event (null for LOAN_CREATED) */
    @Enumerated(EnumType.STRING)
    @Column
    val fromStatus: LoanStatus? = null,

    /** Loan status after this event */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val toStatus: LoanStatus,

    /** Human-readable description of the event, optionally including financial details */
    @Column(length = 500)
    val description: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
