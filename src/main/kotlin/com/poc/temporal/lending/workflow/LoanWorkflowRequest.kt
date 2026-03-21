package com.poc.temporal.lending.workflow

import com.poc.temporal.lending.domain.enums.ProductType
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import java.math.BigDecimal

/**
 * Input DTO for the LoanLifecycleWorkflow. Must be serialisable by Temporal's Jackson converter.
 * Default values provide a no-arg constructor for Jackson deserialization.
 */
data class LoanWorkflowRequest(
    val loanId: Long = 0,
    val borrowerId: String = "",
    val productType: ProductType = ProductType.TERM_LOAN,
    val interestAccrualMethod: InterestAccrualMethod = InterestAccrualMethod.DAILY,
    val principalAmount: BigDecimal = BigDecimal.ZERO,
    val annualInterestRate: BigDecimal = BigDecimal.ZERO,
    val flatInterestRate: BigDecimal? = null,
    val numberOfPaymentCycles: Int = 1,
    val cooldownPeriodDays: Int = 30,
    val lateFeeRate: BigDecimal = BigDecimal("0.05")
)
