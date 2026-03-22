package com.poc.temporal.lending.activity

import com.poc.temporal.lending.domain.Loan
import com.poc.temporal.lending.domain.LoanProduct
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.domain.enums.ProductType
import com.poc.temporal.lending.repository.LedgerEntryRepository
import com.poc.temporal.lending.repository.LoanRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.math.BigDecimal
import java.util.Optional

class LedgerActivitiesTest {

    private lateinit var loanRepository: LoanRepository
    private lateinit var ledgerEntryRepository: LedgerEntryRepository
    private lateinit var ledgerActivities: LedgerActivitiesImpl

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
        ledgerEntryRepository = mock()
        ledgerActivities = LedgerActivitiesImpl(loanRepository, ledgerEntryRepository)

        loan = Loan(
            id = 1L,
            product = product,
            borrowerId = "test-borrower",
            principalAmount = BigDecimal("1000.00"),
            outstandingBalance = BigDecimal("1000.00"),
            accruedInterest = BigDecimal.ZERO,
            lateFees = BigDecimal.ZERO,
            status = LoanStatus.ACTIVE
        )

        whenever(loanRepository.findById(1L)).thenReturn(Optional.of(loan))
        whenever(loanRepository.save(any())).thenAnswer { it.arguments[0] }
        whenever(ledgerEntryRepository.save(any())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `recordDisbursement creates a ledger entry`() {
        ledgerActivities.recordDisbursement(1L, BigDecimal("1000.00"))
        verify(ledgerEntryRepository).save(argThat { entryType.name == "DISBURSEMENT" })
    }

    @Test
    fun `recordDailyInterest adds interest to loan balance`() {
        val dailyRate = BigDecimal("0.000493") // ~18%/365
        ledgerActivities.recordDailyInterest(1L, dailyRate)

        assertEquals(BigDecimal("0.49"), loan.accruedInterest)
        assertEquals(BigDecimal("1000.49"), loan.outstandingBalance)
        verify(ledgerEntryRepository).save(argThat { entryType.name == "INTEREST_ACCRUAL" })
    }

    @Test
    fun `recordLateFee adds fee to loan balance`() {
        ledgerActivities.recordLateFee(1L, BigDecimal("1000.00"), BigDecimal("0.05"))

        assertEquals(BigDecimal("50.00"), loan.lateFees)
        assertEquals(BigDecimal("1050.00"), loan.outstandingBalance)
        verify(ledgerEntryRepository).save(argThat { entryType.name == "LATE_FEE" })
    }

    @Test
    fun `recordUpfrontInterest posts interest to balance`() {
        ledgerActivities.recordUpfrontInterest(1L, BigDecimal("80.00"))

        assertEquals(BigDecimal("80.00"), loan.accruedInterest)
        assertEquals(BigDecimal("1080.00"), loan.outstandingBalance)
    }

    @Test
    fun `getOutstandingBalance returns current balance`() {
        val balance = ledgerActivities.getOutstandingBalance(1L)
        assertEquals(BigDecimal("1000.00"), balance)
    }

    @Test
    fun `recordDailyInterest on unknown loan throws`() {
        whenever(loanRepository.findById(99L)).thenReturn(Optional.empty())
        assertThrows<IllegalArgumentException> {
            ledgerActivities.recordDailyInterest(99L, BigDecimal("0.0005"))
        }
    }
}
