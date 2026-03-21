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
 * Tests for the [BulletLoanWorkflowImpl] workflow.
 *
 * Covers: upfront interest charge, single payment by maturity, maturity default + late fee,
 * paid-off + cooldown sequence, and query methods.
 */
class BulletLoanWorkflowTest {

    private lateinit var testEnv: io.temporal.testing.TestWorkflowEnvironment
    private lateinit var loanAct: TestLoanActivities
    private lateinit var ledgerAct: TestLedgerActivities

    @BeforeEach
    fun setUp() {
        testEnv = buildTestEnvironment()
        val worker = testEnv.newWorker(TEST_TASK_QUEUE)
        worker.registerWorkflowImplementationTypes(BulletLoanWorkflowImpl::class.java)

        loanAct = TestLoanActivities()
        ledgerAct = TestLedgerActivities()
        worker.registerActivitiesImplementations(loanAct, ledgerAct)
        testEnv.start()
    }

    @AfterEach
    fun tearDown() = testEnv.close()

    private fun newRequest(cycles: Int = 1) = LoanWorkflowRequest(
        loanId = 2L,
        borrowerId = "borrower-bullet",
        productType = ProductType.BULLET_LOAN,
        interestAccrualMethod = InterestAccrualMethod.FIXED_UPFRONT,
        principalAmount = BigDecimal("1000.00"),
        annualInterestRate = BigDecimal("0.32"),
        flatInterestRate = BigDecimal("0.08"),
        numberOfPaymentCycles = cycles,
        cooldownPeriodDays = 5,
        lateFeeRate = BigDecimal("0.05")
    )

    private fun newStub() = testEnv.workflowClient.newWorkflowStub(
        BulletLoanWorkflow::class.java,
        WorkflowOptions.newBuilder()
            .setTaskQueue(TEST_TASK_QUEUE)
            .setWorkflowId("test-bullet-${System.nanoTime()}")
            .build()
    )

    @Test
    fun `payment before maturity leads to paid-off and cooldown`() {
        loanAct.balanceAfterPayment = BigDecimal.ZERO
        ledgerAct.stubbedBalance = BigDecimal.ZERO

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest())

        testEnv.sleep(Duration.ofSeconds(1))
        stub.receivePayment(BigDecimal("1080.00"), "PAY-B-001")
        testEnv.sleep(Duration.ofDays(6))   // clear cooldown (5 days)

        assertTrue(loanAct.disbursedLoans.contains(2L), "Loan should have been disbursed")
        assertTrue(ledgerAct.disbursements.any { it.first == 2L }, "Disbursement entry expected")
        assertTrue(ledgerAct.upfrontInterests.any { it.first == 2L }, "Upfront interest entry expected")
        assertTrue(ledgerAct.payments.any { it.first == 2L }, "Payment ledger entry expected")
        assertTrue(loanAct.paidOffLoans.contains(2L), "Loan should be marked paid off")
        assertTrue(loanAct.cooldownStarted.contains(2L), "Cooldown should have started")
        assertTrue(loanAct.cooldownEnded.contains(2L), "Cooldown should have ended")
    }

    @Test
    fun `upfront interest is calculated from flat rate and added to balance`() {
        ledgerAct.stubbedBalance = BigDecimal("1080.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest())

        testEnv.sleep(Duration.ofSeconds(1))

        // 8% flat on $1,000 = $80 upfront interest
        val expectedInterest = BigDecimal("80.00")
        assertTrue(
            ledgerAct.upfrontInterests.any { it.first == 2L && it.second == expectedInterest },
            "Upfront interest should be $expectedInterest"
        )
    }

    @Test
    fun `no payment by maturity marks loan delinquent and charges late fee`() {
        ledgerAct.stubbedBalance = BigDecimal("1080.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest(cycles = 1))

        // Fast-forward past maturity (30 days) + full extended grace period (30 days)
        testEnv.sleep(Duration.ofDays(61))

        assertTrue(loanAct.delinquentLoans.contains(2L), "Loan should be marked delinquent")
        assertTrue(ledgerAct.lateFees.contains(2L), "Late fee should have been recorded")
    }

    @Test
    fun `query methods return active status and correct balance after disbursement`() {
        ledgerAct.stubbedBalance = BigDecimal("1080.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, newRequest())

        testEnv.sleep(Duration.ofSeconds(1))

        assertEquals("ACTIVE", stub.getLoanStatus())
        assertTrue(stub.getOutstandingBalance() > BigDecimal.ZERO)
        assertFalse(stub.isInCooldown())
    }
}
