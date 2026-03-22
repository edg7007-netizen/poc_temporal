package com.poc.temporal.lending.workflow

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.poc.temporal.lending.activity.LedgerActivities
import com.poc.temporal.lending.activity.LoanActivities
import io.temporal.client.WorkflowClientOptions
import io.temporal.common.converter.DefaultDataConverter
import io.temporal.common.converter.JacksonJsonPayloadConverter
import io.temporal.common.converter.NullPayloadConverter
import io.temporal.testing.TestEnvironmentOptions
import io.temporal.testing.TestWorkflowEnvironment
import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList

const val TEST_TASK_QUEUE = "test-lending-queue"

/**
 * Concrete test-double for [LoanActivities].
 *
 * Temporal rejects Mockito proxies that inherit @ActivityMethod annotations, so we use
 * a hand-rolled test double that simply records calls and returns configurable values.
 */
open class TestLoanActivities : LoanActivities {
    val disbursedLoans = CopyOnWriteArrayList<Long>()
    val paidOffLoans = CopyOnWriteArrayList<Long>()
    val delinquentLoans = CopyOnWriteArrayList<Long>()
    val cooldownStarted = CopyOnWriteArrayList<Long>()
    val cooldownEnded = CopyOnWriteArrayList<Long>()
    val cancelledLoans = CopyOnWriteArrayList<Pair<Long, String>>()
    val updatedStatuses = CopyOnWriteArrayList<Pair<Long, String>>()
    var balanceAfterPayment: BigDecimal = BigDecimal.ZERO

    override fun disburseLoan(loanId: Long) { disbursedLoans.add(loanId) }
    override fun applyPayment(loanId: Long, amount: BigDecimal, referenceNumber: String) = balanceAfterPayment
    override fun markDelinquent(loanId: Long) { delinquentLoans.add(loanId) }
    override fun markPaidOff(loanId: Long) { paidOffLoans.add(loanId) }
    override fun cancelLoan(loanId: Long, reason: String) { cancelledLoans.add(loanId to reason) }
    override fun startCooldown(loanId: Long) { cooldownStarted.add(loanId) }
    override fun endCooldown(loanId: Long) { cooldownEnded.add(loanId) }
    override fun updateLoanStatus(loanId: Long, status: String) { updatedStatuses.add(loanId to status) }
}

/**
 * Concrete test-double for [LedgerActivities].
 */
class TestLedgerActivities : LedgerActivities {
    val disbursements = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    val upfrontInterests = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    val dailyInterestCallCount = CopyOnWriteArrayList<Long>()
    val lateFees = CopyOnWriteArrayList<Long>()
    val payments = CopyOnWriteArrayList<Pair<Long, BigDecimal>>()
    var stubbedBalance: BigDecimal = BigDecimal("1000.00")

    override fun recordDisbursement(loanId: Long, amount: BigDecimal) { disbursements.add(loanId to amount) }
    override fun recordUpfrontInterest(loanId: Long, amount: BigDecimal) { upfrontInterests.add(loanId to amount) }
    override fun recordDailyInterest(loanId: Long, dailyRate: BigDecimal) { dailyInterestCallCount.add(loanId) }
    override fun recordLateFee(loanId: Long, overdueAmount: BigDecimal, lateFeeRate: BigDecimal) { lateFees.add(loanId) }
    override fun recordPayment(loanId: Long, amount: BigDecimal, referenceId: String?) { payments.add(loanId to amount) }
    override fun getOutstandingBalance(loanId: Long): BigDecimal = stubbedBalance
}

/** Builds a [TestWorkflowEnvironment] configured with a Kotlin-aware Jackson data converter. */
fun buildTestEnvironment(): TestWorkflowEnvironment {
    val mapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val dataConverter = DefaultDataConverter(
        NullPayloadConverter(),
        JacksonJsonPayloadConverter(mapper)
    )

    return TestWorkflowEnvironment.newInstance(
        TestEnvironmentOptions.newBuilder()
            .setWorkflowClientOptions(
                WorkflowClientOptions.newBuilder()
                    .setDataConverter(dataConverter)
                    .build()
            )
            .build()
    )
}
