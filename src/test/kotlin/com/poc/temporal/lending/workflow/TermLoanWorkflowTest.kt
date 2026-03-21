package com.poc.temporal.lending.workflow

import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.ProductType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration

/**
 * Tests for the [TermLoanWorkflowImpl] workflow.
 *
 * Covers: daily interest accrual, payment-per-cycle, late-fee + grace period on miss,
 * paid-off + cooldown sequence, and query methods.
 */
class TermLoanWorkflowTest {

    private lateinit var testEnv: io.temporal.testing.TestWorkflowEnvironment
    private lateinit var loanAct: TestLoanActivities
    private lateinit var ledgerAct: TestLedgerActivities

    @BeforeEach
    fun setUp() {
        testEnv = buildTestEnvironment()
        val worker = testEnv.newWorker(TEST_TASK_QUEUE)
        worker.registerWorkflowImplementationTypes(TermLoanWorkflowImpl::class.java)

        loanAct = TestLoanActivities()
        ledgerAct = TestLedgerActivities()
        worker.registerActivitiesImplementations(loanAct, ledgerAct)
        testEnv.start()
    }

    @AfterEach
    fun tearDown() = testEnv.close()

    private fun newRequest(cycles: Int = 3) = LoanWorkflowRequest(
        loanId = 1L,
        borrowerId = "borrower-term",
        productType = ProductType.TERM_LOAN,
        interestAccrualMethod = InterestAccrualMethod.DAILY,
        principalAmount = BigDecimal("1000.00"),
        annualInterestRate = BigDecimal("0.18"),
        flatInterestRate = null,
        numberOfPaymentCycles = cycles,
        cooldownPeriodDays = 5,
        lateFeeRate = BigDecimal("0.05")
    )

    private fun newStub() = testEnv.workflowClient.newWorkflowStub(
        TermLoanWorkflow::class.java,
        WorkflowOptions.newBuilder()
            .setTaskQueue(TEST_TASK_QUEUE)
            .setWorkflowId("test-term-${System.nanoTime()}")
            .build()
    )

    @Test
    fun `payment within first cycle triggers paid-off and cooldown`() {
        loanAct.balanceAfterPayment = BigDecimal.ZERO
        ledgerAct.stubbedBalance = BigDecimal.ZERO

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest(cycles = 1))

        testEnv.sleep(Duration.ofDays(1))       // let first day's interest accrue
        stub.receivePayment(BigDecimal("1001.00"), "PAY-T-001")
        testEnv.sleep(Duration.ofDays(6))       // clear cooldown (5 days)

        assertTrue(loanAct.disbursedLoans.contains(1L), "Loan should have been disbursed")
        assertTrue(ledgerAct.disbursements.any { it.first == 1L }, "Disbursement ledger entry expected")
        assertTrue(ledgerAct.dailyInterestCallCount.contains(1L), "Daily interest should have accrued")
        assertTrue(ledgerAct.payments.any { it.first == 1L }, "Payment ledger entry expected")
        assertTrue(loanAct.paidOffLoans.contains(1L), "Loan should be marked paid off")
        assertTrue(loanAct.cooldownStarted.contains(1L), "Cooldown should have started")
        assertTrue(loanAct.cooldownEnded.contains(1L), "Cooldown should have ended")
    }

    @Test
    fun `missed cycle payment triggers delinquency and late fee`() {
        ledgerAct.stubbedBalance = BigDecimal("1010.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest(cycles = 1))

        // Advance past the full 30-day cycle without sending a payment
        testEnv.sleep(Duration.ofDays(31))

        assertTrue(loanAct.delinquentLoans.contains(1L), "Loan should be marked delinquent")
        assertTrue(ledgerAct.lateFees.contains(1L), "Late fee should have been recorded")
    }

    @Test
    fun `payment during grace period reinstates loan to ACTIVE`() {
        ledgerAct.stubbedBalance = BigDecimal("1010.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest(cycles = 1))

        // Advance past the full 30-day cycle without any payment signal
        testEnv.sleep(Duration.ofDays(31))

        // Loan should now be DELINQUENT; send payment within the 15-day grace period
        stub.receivePayment(BigDecimal("1010.00"), "PAY-T-GRACE")
        testEnv.sleep(Duration.ofDays(1))

        assertTrue(loanAct.delinquentLoans.contains(1L), "Loan should have been marked delinquent")
        assertTrue(
            loanAct.updatedStatuses.any { it.first == 1L && it.second == "ACTIVE" },
            "Loan should be reinstated to ACTIVE after grace period payment"
        )
    }

    @Test
    fun `query methods return active status and non-zero balance after disbursement`() {
        ledgerAct.stubbedBalance = BigDecimal("1000.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest(cycles = 2))

        testEnv.sleep(Duration.ofSeconds(1))

        assertEquals("ACTIVE", stub.getLoanStatus())
        assertTrue(stub.getOutstandingBalance() > BigDecimal.ZERO)
        assertFalse(stub.isInCooldown())
    }
}
