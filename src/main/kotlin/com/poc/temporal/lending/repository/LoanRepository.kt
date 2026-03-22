package com.poc.temporal.lending.repository

import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.domain.enums.LoanStatus
import org.springframework.data.jpa.repository.JpaRepository

interface LoanRepository : JpaRepository<Loan, Long> {
    fun findByBorrowerId(borrowerId: String): List<Loan>
    fun findByBorrowerIdAndStatusIn(borrowerId: String, statuses: List<LoanStatus>): List<Loan>
    fun findByWorkflowId(workflowId: String): Loan?
    fun findByStatus(status: LoanStatus): List<Loan>
}
