package com.poc.temporal.lending.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal

data class CreateLoanRequest(
    @field:NotBlank val borrowerId: String,
    @field:Positive val productId: Long,
    @field:DecimalMin("0.01") val principalAmount: BigDecimal
)

data class PaymentRequest(
    @field:DecimalMin("0.01") val amount: BigDecimal,
    val referenceNumber: String = ""
)

data class CancelLoanRequest(
    @field:NotBlank val reason: String
)
