package com.poc.temporal.lending.service

import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.domain.enums.ProductType
import com.poc.temporal.lending.repository.LoanProductRepository
import com.poc.temporal.lending.repository.LoanRepository
import com.poc.temporal.lending.workflow.CDDWorkflow
import com.poc.temporal.lending.workflow.InstallmentsWorkflow
import com.poc.temporal.lending.workflow.LoanWorkflow
import com.poc.temporal.lending.workflow.LoanWorkflowRequest
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class LoanService(
    private val loanRepository: LoanRepository,
    private val loanProductRepository: LoanProductRepository,
    private val workflowClient: WorkflowClient
) {

    @Transactional
    fun createLoan(borrowerId: String, productId: Long, principalAmount: BigDecimal): Loan {
        val product = loanProductRepository.findById(productId)
            .orElseThrow { IllegalArgumentException("Product $productId not found") }

        validateLoanCreation(borrowerId, product, principalAmount)

        val loan = loanRepository.save(
            Loan(
                product = product,
                borrowerId = borrowerId,
                principalAmount = principalAmount,
                status = LoanStatus.APPROVED
            )
        )

        val workflowId = "loan-${loan.id}"
        loan.workflowId = workflowId
        loanRepository.save(loan)

        val options = WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId)
            .setTaskQueue("lending-task-queue")
            .build()

        val request = LoanWorkflowRequest(
            loanId = loan.id,
            borrowerId = borrowerId,
            productType = product.productType,
            interestAccrualMethod = product.interestAccrualMethod,
            principalAmount = principalAmount,
            annualInterestRate = product.annualInterestRate,
            flatInterestRate = product.flatInterestRate,
            numberOfPaymentCycles = product.numberOfPaymentCycles,
            cooldownPeriodDays = product.cooldownPeriodDays,
            lateFeeRate = product.lateFeeRate
        )

        startWorkflow(product.productType, options, request)

        return loan
    }

    fun processPayment(loanId: Long, amount: BigDecimal, referenceNumber: String) {
        val loan = loanRepository.findById(loanId)
            .orElseThrow { IllegalArgumentException("Loan $loanId not found") }

        if (loan.status !in listOf(LoanStatus.ACTIVE, LoanStatus.DELINQUENT)) {
            throw IllegalStateException("Cannot accept payment for loan in status ${loan.status}")
        }

        workflowStubFor(loan).receivePayment(amount, referenceNumber)
    }

    fun cancelLoan(loanId: Long, reason: String) {
        val loan = loanRepository.findById(loanId)
            .orElseThrow { IllegalArgumentException("Loan $loanId not found") }

        if (loan.status !in listOf(LoanStatus.PENDING, LoanStatus.APPROVED)) {
            throw IllegalStateException("Cannot cancel loan in status ${loan.status}")
        }

        workflowStubFor(loan).cancelLoan(reason)
    }

    fun getLoanStatus(loanId: Long): Map<String, Any> {
        val loan = loanRepository.findById(loanId)
            .orElseThrow { IllegalArgumentException("Loan $loanId not found") }

        var workflowStatus = loan.status.name
        var balance = loan.outstandingBalance
        var cooldown = false

        if (loan.workflowId != null) {
            try {
                val stub = workflowStubFor(loan)
                workflowStatus = stub.getLoanStatus()
                balance = stub.getOutstandingBalance()
                cooldown = stub.isInCooldown()
            } catch (e: Exception) {
                // Workflow may have completed; fall back to DB values
            }
        }

        return mapOf(
            "loanId" to loanId,
            "borrowerId" to loan.borrowerId,
            "status" to workflowStatus,
            "outstandingBalance" to balance,
            "inCooldown" to cooldown,
            "productName" to loan.product.name
        )
    }

    fun getLoan(loanId: Long): Loan =
        loanRepository.findById(loanId)
            .orElseThrow { IllegalArgumentException("Loan $loanId not found") }

    fun getLoansByBorrower(borrowerId: String): List<Loan> =
        loanRepository.findByBorrowerId(borrowerId)

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Starts the correct workflow type for the given product. */
    private fun startWorkflow(productType: ProductType, options: WorkflowOptions, request: LoanWorkflowRequest) {
        when (productType) {
            ProductType.INSTALLMENTS -> {
                val stub = workflowClient.newWorkflowStub(InstallmentsWorkflow::class.java, options)
                WorkflowClient.start(stub::execute, request)
            }
            ProductType.CDD -> {
                val stub = workflowClient.newWorkflowStub(CDDWorkflow::class.java, options)
                WorkflowClient.start(stub::execute, request)
            }
        }
    }

    /**
     * Returns a [LoanWorkflow] stub for the running workflow of the given loan,
     * selecting the correct workflow type from the loan's product.
     */
    private fun workflowStubFor(loan: Loan): LoanWorkflow {
        val workflowId = loan.workflowId ?: throw IllegalStateException("Loan ${loan.id} has no workflow")
        return when (loan.product.productType) {
            ProductType.INSTALLMENTS ->
                workflowClient.newWorkflowStub(InstallmentsWorkflow::class.java, workflowId)
            ProductType.CDD ->
                workflowClient.newWorkflowStub(CDDWorkflow::class.java, workflowId)
        }
    }

    private fun validateLoanCreation(borrowerId: String, product: LoanProduct, amount: BigDecimal) {
        require(product.active) { "Product ${product.name} is not active" }
        require(amount >= product.minLoanAmount) { "Amount below minimum ${product.minLoanAmount}" }
        require(amount <= product.maxLoanAmount) { "Amount exceeds maximum ${product.maxLoanAmount}" }

        val activeLoans = loanRepository.findByBorrowerIdAndStatusIn(
            borrowerId,
            listOf(LoanStatus.ACTIVE, LoanStatus.DELINQUENT, LoanStatus.IN_COOLDOWN)
        )
        require(activeLoans.isEmpty()) {
            "Borrower $borrowerId has an existing active or cooldown loan"
        }
    }
}

