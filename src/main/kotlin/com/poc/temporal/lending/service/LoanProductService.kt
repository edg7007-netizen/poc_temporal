package com.poc.temporal.lending.service

import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.repository.LoanProductRepository
import org.springframework.stereotype.Service

@Service
class LoanProductService(private val loanProductRepository: LoanProductRepository) {

    fun getAllActiveProducts(): List<LoanProduct> = loanProductRepository.findByActiveTrue()

    fun getProduct(id: Long): LoanProduct =
        loanProductRepository.findById(id).orElseThrow { IllegalArgumentException("Product $id not found") }

    fun createProduct(product: LoanProduct): LoanProduct = loanProductRepository.save(product)
}
