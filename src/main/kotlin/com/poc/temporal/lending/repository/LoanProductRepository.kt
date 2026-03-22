package com.poc.temporal.lending.repository

import com.poc.temporal.lending.domain.LoanProduct
import org.springframework.data.jpa.repository.JpaRepository

interface LoanProductRepository : JpaRepository<LoanProduct, Long> {
    fun findByActiveTrue(): List<LoanProduct>
    fun findByName(name: String): LoanProduct?
}
