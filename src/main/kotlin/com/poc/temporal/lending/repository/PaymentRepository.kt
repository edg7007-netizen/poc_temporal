package com.poc.temporal.lending.repository

import com.poc.temporal.lending.domain.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentRepository : JpaRepository<Payment, Long> {
    fun findByLoanIdOrderByPaymentDateAsc(loanId: Long): List<Payment>
}
