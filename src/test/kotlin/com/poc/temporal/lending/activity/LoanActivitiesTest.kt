package com.poc.temporal.lending.activity

import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.domain.enums.ProductType
import com.poc.temporal.lending.repository.LoanRepository
import com.poc.temporal.lending.repository.PaymentRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.Optional

class LoanActivitiesTest {

    private lateinit var loanRepository: LoanRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var loanActivities: LoanActivitiesImpl

    private val product = LoanProduct(
        id = 1L,
        name = "Test Product",
        productType = ProductType.INSTALLMENTS,
        interestAccrualMethod = InterestAccrualMethod.DAILY,
        annualInterestRate = BigDecimal("0.18"),
        numberOfPaymentCycles = 12,
        maxLoanAmount = BigDecimal("50000"),
        minLoanAmount = BigDecimal("500"),
        cooldownPeriodDays = 30,
        lateFeeRate = BigDecimal("0.05")
    )

    private lateinit var loan: Loan

    @BeforeEach
    fun setUp() {
        loanRepository = mock()
        paymentRepository = mock()
        loanActivities = LoanActivitiesImpl(loanRepository, paymentRepository)

        loan = Loan(
            id = 1L,
            product = product,
            borrowerId = "test-borrower",
            principalAmount = BigDecimal("1000.00"),
            outstandingBalance = BigDecimal("1000.00"),
            status = LoanStatus.APPROVED
        )

        whenever(loanRepository.findById(1L)).thenReturn(Optional.of(loan))
        whenever(loanRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(paymentRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `disburseLoan sets status to ACTIVE and sets dates`() {
        loanActivities.disburseLoan(1L)
        assertEquals(LoanStatus.ACTIVE, loan.status)
        assertNotNull(loan.disbursementDate)
        assertNotNull(loan.maturityDate)
    }

    @Test
    fun `applyPayment reduces outstanding balance`() {
        loan.status = LoanStatus.ACTIVE
        loan.accruedInterest = BigDecimal("50.00")
        loan.outstandingBalance = BigDecimal("1050.00")

        val remaining = loanActivities.applyPayment(1L, BigDecimal("1050.00"), "PAY-001")

        assertEquals(BigDecimal.ZERO, remaining)
        assertEquals(1, loan.paymentsMade)
        assertEquals(BigDecimal("1050.00"), loan.totalPaid)
    }

    @Test
    fun `cancelLoan changes status to CANCELLED`() {
        loanActivities.cancelLoan(1L, "Customer request")
        assertEquals(LoanStatus.CANCELLED, loan.status)
        assertEquals("Customer request", loan.notes)
    }

    @Test
    fun `cancelLoan on active loan throws`() {
        loan.status = LoanStatus.ACTIVE
        assertThrows<IllegalStateException> {
            loanActivities.cancelLoan(1L, "Too late")
        }
    }

    @Test
    fun `markDelinquent sets status to DELINQUENT`() {
        loan.status = LoanStatus.ACTIVE
        loanActivities.markDelinquent(1L)
        assertEquals(LoanStatus.DELINQUENT, loan.status)
    }

    @Test
    fun `markPaidOff sets status and zeroes balance`() {
        loan.status = LoanStatus.ACTIVE
        loanActivities.markPaidOff(1L)
        assertEquals(LoanStatus.PAID_OFF, loan.status)
        assertEquals(BigDecimal.ZERO, loan.outstandingBalance)
    }

    @Test
    fun `startCooldown sets IN_COOLDOWN and cooldown end date`() {
        loan.status = LoanStatus.PAID_OFF
        loanActivities.startCooldown(1L)
        assertEquals(LoanStatus.IN_COOLDOWN, loan.status)
        assertNotNull(loan.cooldownEndDate)
    }

    @Test
    fun `endCooldown sets PAID_OFF`() {
        loan.status = LoanStatus.IN_COOLDOWN
        loanActivities.endCooldown(1L)
        assertEquals(LoanStatus.PAID_OFF, loan.status)
    }

    @Test
    fun `disburseLoan on unknown loan throws`() {
        whenever(loanRepository.findById(99L)).thenReturn(Optional.empty())
        assertThrows<IllegalArgumentException> {
            loanActivities.disburseLoan(99L)
        }
    }
}
