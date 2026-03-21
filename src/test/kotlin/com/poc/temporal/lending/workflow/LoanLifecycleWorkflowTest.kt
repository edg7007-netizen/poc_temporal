package com.poc.temporal.lending.workflow

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.poc.temporal.lending.activity.LedgerActivities
import com.poc.temporal.lending.activity.LoanActivities
import com.poc.temporal.lending.domain.enums.InterestAccrualMethod
import com.poc.temporal.lending.domain.enums.ProductType
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowClientOptions
import io.temporal.client.WorkflowOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.common.converter.NullPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import io.temporal.worker.Worker
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Concrete test-double for LoanActivities (avoids Temporal rejecting Mockito-generated
 * proxy classes that inherit @ActivityMethod annotations).
 */
class TestLoanActivities : LoanActivities {
    val disbursedLoans = CopyOnWriteArrayList<Long>()
    val paidOffLoans = CopyOnWriteArrayList<Long>()
    val delinquentLoans = CopyOnWriteArrayList<Long>()
    val cooldownStarted = CopyOnWriteArrayList<Long>()
    val cooldownEnded = CopyOnWriteArrayList<Long>()
    val cancelledLoans = CopyOnWriteArrayList<Pair<Long, String>>()
    var balanceAfterPayment: BigDecimal = BigDecimal.ZERO

    override fun disburseLoan(loanId: Long) { disbursedLoans.add(loanId) }
    override fun applyPayment(loanId: Long, amount: BigDecimal, referenceNumber: String): BigDecimal {
        return balanceAfterPayment
    }
    override fun markDelinquent(loanId: Long) { delinquentLoans.add(loanId) }
    override fun markPaidOff(loanId: Long) { paidOffLoans.add(loanId) }
    override fun cancelLoan(loanId: Long, reason: String) { cancelledLoans.add(loanId to reason) }
    override fun startCooldown(loanId: Long) { cooldownStarted.add(loanId) }
    override fun endCooldown(loanId: Long) { cooldownEnded.add(loanId) }
    override fun updateLoanStatus(loanId: Long, status: String) {}
}

/**
 * Concrete test-double for LedgerActivities.
 */
class TestLedgerActivities : LedgerActivities {
    val disbursements = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    val upfrontInterests = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    val dailyInterests = CopyOnWriteArrayList<Long>()
    val lateFees = CopyOnWriteArrayList<Long>()
    val payments = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    var stubbedBalance: BigDecimal = BigDecimal("1000.00")

    override fun recordDisbursement(loanId: Long, amount: BigDecimal) { disbursements.add(loanId to amount) }
    override fun recordUpfrontInterest(loanId: Long, amount: BigDecimal) { upfrontInterests.add(loanId to amount) }
    override fun recordDailyInterest(loanId: Long, dailyRate: BigDecimal) { dailyInterests.add(loanId) }
    override fun recordLateFee(loanId: Long, overdueAmount: BigDecimal, lateFeeRate: BigDecimal) { lateFees.add(loanId) }
    override fun recordPayment(loanId: Long, amount: BigDecimal, referenceId: String?) { payments.add(loanId to amount) }
    override fun getOutstandingBalance(loanId: Long): BigDecimal = stubbedBalance
}

class LoanLifecycleWorkflowTest {

    private lateinit var testEnv: TestWorkflowEnvironment
    private lateinit var worker: Worker
    private lateinit var client: WorkflowClient
    private lateinit var loanAct: TestLoanActivities
    private lateinit var ledgerAct: TestLedgerActivities

    private val taskQueue = "test-lending-queue"

    @BeforeEach
    fun setUp() {
        val mapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        val dataConverter = DefaultDataConverter(
            NullPayloadConverter(),
            JacksonJsonPayloadConverter(mapper)
        )

        testEnv = TestWorkflowEnvironment.newInstance(
            TestEnvironmentOptions.newBuilder()
                .setWorkflowClientOptions(
                    WorkflowClientOptions.newBuilder()
                        .setDataConverter(dataConverter)
                        .build()
                )
                .build()
        )
        worker = testEnv.newWorker(taskQueue)
        worker.registerWorkflowImplementationTypes(LoanLifecycleWorkflowImpl::class.java)

        loanAct = TestLoanActivities()
        ledgerAct = TestLedgerActivities()

        worker.registerActivitiesImplementations(loanAct, ledgerAct)
        testEnv.start()
        client = testEnv.workflowClient
    }

    @AfterEach
    fun tearDown() {
        testEnv.close()
    }

    private fun buildRequest(
        productType: ProductType = ProductType.BULLET_LOAN,
        interestAccrualMethod: InterestAccrualMethod = InterestAccrualMethod.FIXED_UPFRONT,
        cycles: Int = 1,
        flatRate: BigDecimal? = BigDecimal("0.08")
    ) = LoanWorkflowRequest(
        loanId = 1L,
        borrowerId = "borrower-001",
        productType = productType,
        interestAccrualMethod = interestAccrualMethod,
        principalAmount = BigDecimal("1000.00"),
        annualInterestRate = BigDecimal("0.18"),
        flatInterestRate = flatRate,
        numberOfPaymentCycles = cycles,
        cooldownPeriodDays = 5,
        lateFeeRate = BigDecimal("0.05")
    )

    private fun newStub(): LoanLifecycleWorkflow = client.newWorkflowStub(
        LoanLifecycleWorkflow::class.java,
        WorkflowOptions.newBuilder()
            .setTaskQueue(taskQueue)
            .setWorkflowId("test-loan-${System.nanoTime()}")
            .build()
    )

    @Test
    fun `bullet loan - payment signal results in paid off and cooldown`() {
        // Balance returns 0 after payment so the workflow transitions to paid-off
        loanAct.balanceAfterPayment = BigDecimal.ZERO
        ledgerAct.stubbedBalance = BigDecimal.ZERO

        val stub = newStub()
        WorkflowClient.start(stub::execute, buildRequest())

        testEnv.sleep(Duration.ofSeconds(1))
        stub.receivePayment(BigDecimal("1080.00"), "PAY-001")
        testEnv.sleep(Duration.ofDays(6))

        assertTrue(loanAct.disbursedLoans.contains(1L), "Loan should have been disbursed")
        assertTrue(ledgerAct.disbursements.any { it.first == 1L }, "Disbursement ledger entry expected")
        assertTrue(ledgerAct.upfrontInterests.any { it.first == 1L }, "Upfront interest entry expected")
        assertTrue(ledgerAct.payments.any { it.first == 1L }, "Payment ledger entry expected")
        assertTrue(loanAct.paidOffLoans.contains(1L), "Loan should be paid off")
        assertTrue(loanAct.cooldownStarted.contains(1L), "Cooldown should have started")
        assertTrue(loanAct.cooldownEnded.contains(1L), "Cooldown should have ended")
    }

    @Test
    fun `query methods return consistent values after start`() {
        loanAct.balanceAfterPayment = BigDecimal("500.00")
        ledgerAct.stubbedBalance = BigDecimal("500.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, buildRequest())

        testEnv.sleep(Duration.ofSeconds(1))

        val status = stub.getLoanStatus()
        val balance = stub.getOutstandingBalance()
        val cooldown = stub.isInCooldown()

        assertNotNull(status)
        assertNotNull(balance)
        assertFalse(cooldown)
    }

    @Test
    fun `bullet loan becomes delinquent when no payment by maturity`() {
        ledgerAct.stubbedBalance = BigDecimal("1080.00")

        val stub = newStub()
        WorkflowClient.start(stub::execute, buildRequest(cycles = 1))

        // Fast-forward past maturity (30 days) and grace period (30 days)
        testEnv.sleep(Duration.ofDays(61))

        assertTrue(loanAct.delinquentLoans.contains(1L), "Loan should be marked delinquent")
        assertTrue(ledgerAct.lateFees.contains(1L), "Late fee should be recorded")
    }
}
