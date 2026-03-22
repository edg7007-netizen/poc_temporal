package com.poc.temporal.lending.repository

import com.poc.temporal.lending.domain.WorkflowEvent
import org.springframework.data.jpa.repository.JpaRepository

interface WorkflowEventRepository : JpaRepository<WorkflowEvent, Long> {
    fun findByLoanIdOrderByCreatedAtAsc(loanId: Long): List<WorkflowEvent>
}
