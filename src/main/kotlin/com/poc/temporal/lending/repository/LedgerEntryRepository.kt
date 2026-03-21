package com.poc.temporal.lending.repository

import com.poc.temporal.lending.domain.LedgerEntry
import com.poc.temporal.lending.domain.enums.LedgerEntryType
import org.springframework.data.jpa.repository.JpaRepository

interface LedgerEntryRepository : JpaRepository<LedgerEntry, Long> {
    fun findByLoanIdOrderByCreatedAtAsc(loanId: Long): List<LedgerEntry>
    fun findByLoanIdAndEntryType(loanId: Long, entryType: LedgerEntryType): List<LedgerEntry>
}
