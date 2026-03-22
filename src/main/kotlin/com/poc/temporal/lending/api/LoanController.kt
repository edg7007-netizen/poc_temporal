package com.poc.temporal.lending.api

import com.poc.temporal.lending.api.dto.CancelLoanRequest
import com.poc.temporal.lending.api.dto.CreateLoanRequest
import com.poc.temporal.lending.api.dto.PaymentRequest
import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.repository.LedgerEntryRepository
import com.poc.temporal.lending.repository.PaymentRepository
import com.poc.temporal.lending.repository.WorkflowEventRepository
import com.poc.temporal.lending.service.LoanService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/loans")
class LoanController(
    private val loanService: LoanService,
    private val ledgerEntryRepository: LedgerEntryRepository,
    private val paymentRepository: PaymentRepository,
    private val workflowEventRepository: WorkflowEventRepository
) {

    /** Create and disburse a new loan */
    @PostMapping
    fun createLoan(@Valid @RequestBody request: CreateLoanRequest): ResponseEntity<Loan> {
        val loan = loanService.createLoan(
            borrowerId = request.borrowerId,
            productId = request.productId,
            principalAmount = request.principalAmount
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(loan)
    }

    /** Get loan details */
    @GetMapping("/{loanId}")
    fun getLoan(@PathVariable loanId: Long): ResponseEntity<Loan> =
        ResponseEntity.ok(loanService.getLoan(loanId))

    /** Get real-time loan status (including Temporal workflow state) */
    @GetMapping("/{loanId}/status")
    fun getLoanStatus(@PathVariable loanId: Long): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(loanService.getLoanStatus(loanId))

    /** Get all loans for a borrower */
    @GetMapping("/borrower/{borrowerId}")
    fun getLoansByBorrower(@PathVariable borrowerId: String): ResponseEntity<List<Loan>> =
        ResponseEntity.ok(loanService.getLoansByBorrower(borrowerId))

    /** Process a payment for a loan */
    @PostMapping("/{loanId}/payments")
    fun processPayment(
        @PathVariable loanId: Long,
        @Valid @RequestBody request: PaymentRequest
    ): ResponseEntity<Map<String, String>> {
        loanService.processPayment(loanId, request.amount, request.referenceNumber)
        return ResponseEntity.ok(mapOf("message" to "Payment of ${request.amount} submitted for loan $loanId"))
    }

    /** Manually cancel a loan (only before disbursement) */
    @PostMapping("/{loanId}/cancel")
    fun cancelLoan(
        @PathVariable loanId: Long,
        @Valid @RequestBody request: CancelLoanRequest
    ): ResponseEntity<Map<String, String>> {
        loanService.cancelLoan(loanId, request.reason)
        return ResponseEntity.ok(mapOf("message" to "Cancellation request submitted for loan $loanId"))
    }

    /** Get ledger entries for a loan */
    @GetMapping("/{loanId}/ledger")
    fun getLedger(@PathVariable loanId: Long): ResponseEntity<Any> =
        ResponseEntity.ok(ledgerEntryRepository.findByLoanIdOrderByCreatedAtAsc(loanId))

    /** Get payment history for a loan */
    @GetMapping("/{loanId}/payments")
    fun getPayments(@PathVariable loanId: Long): ResponseEntity<Any> =
        ResponseEntity.ok(paymentRepository.findByLoanIdOrderByPaymentDateAsc(loanId))

    /** Get Temporal workflow event history for a loan */
    @GetMapping("/{loanId}/events")
    fun getWorkflowEvents(@PathVariable loanId: Long): ResponseEntity<Any> =
        ResponseEntity.ok(workflowEventRepository.findByLoanIdOrderByCreatedAtAsc(loanId))

    /** Global error handler */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleNotFound(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "Not found")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (ex.message ?: "Conflict")))
}
