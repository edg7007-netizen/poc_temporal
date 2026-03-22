package com.poc.temporal.lending.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.poc.temporal.lending.api.dto.CreateLoanRequest
import com.poc.temporal.lending.api.dto.PaymentRequest
import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.domain.enums.ProductType
import com.poc.temporal.lending.repository.LedgerEntryRepository
import com.poc.temporal.lending.repository.PaymentRepository
import com.poc.temporal.lending.service.LoanService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.math.BigDecimal

@WebMvcTest(LoanController::class)
class LoanControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var loanService: LoanService

    @MockBean
    private lateinit var ledgerEntryRepository: LedgerEntryRepository

    @MockBean
    private lateinit var paymentRepository: PaymentRepository

    private val product = LoanProduct(
        id = 1L,
        name = "Personal Term Loan",
        productType = ProductType.TERM_LOAN,
        interestAccrualMethod = InterestAccrualMethod.DAILY,
        annualInterestRate = BigDecimal("0.18"),
        numberOfPaymentCycles = 12,
        maxLoanAmount = BigDecimal("50000"),
        minLoanAmount = BigDecimal("500")
    )

    private val loan = Loan(
        id = 1L,
        product = product,
        borrowerId = "borrower-001",
        principalAmount = BigDecimal("5000.00"),
        status = LoanStatus.ACTIVE,
        outstandingBalance = BigDecimal("5000.00")
    )

    @Test
    fun `POST loans creates loan and returns 201`() {
        whenever(loanService.createLoan(any(), any(), any())).thenReturn(loan)

        val request = CreateLoanRequest(
            borrowerId = "borrower-001",
            productId = 1L,
            principalAmount = BigDecimal("5000.00")
        )

        mockMvc.perform(
            post("/api/loans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.borrowerId").value("borrower-001"))
    }

    @Test
    fun `GET loans by id returns loan`() {
        whenever(loanService.getLoan(1L)).thenReturn(loan)

        mockMvc.perform(get("/api/loans/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
    }

    @Test
    fun `GET loans by id returns 404 when not found`() {
        whenever(loanService.getLoan(99L)).thenThrow(IllegalArgumentException("Not found"))

        mockMvc.perform(get("/api/loans/99"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST payment submits payment`() {
        doNothing().whenever(loanService).processPayment(any(), any(), any())

        val request = PaymentRequest(amount = BigDecimal("500.00"), referenceNumber = "PAY-001")

        mockMvc.perform(
            post("/api/loans/1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `GET status returns real-time status map`() {
        whenever(loanService.getLoanStatus(1L)).thenReturn(
            mapOf(
                "loanId" to 1L,
                "status" to "ACTIVE",
                "outstandingBalance" to BigDecimal("5000.00"),
                "inCooldown" to false,
                "productName" to "Personal Term Loan",
                "borrowerId" to "borrower-001"
            )
        )

        mockMvc.perform(get("/api/loans/1/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ACTIVE"))
    }
}
