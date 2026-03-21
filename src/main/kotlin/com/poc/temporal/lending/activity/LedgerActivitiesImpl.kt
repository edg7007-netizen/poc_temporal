package com.poc.temporal.lending.activity

import com.poc.temporal.lending.domain.LedgerEntry
import com.poc.temporal.lending.domain.enums.LedgerEntryType
import com.poc.temporal.lending.repository.LedgerEntryRepository
import com.poc.temporal.lending.repository.LoanRepository
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Component
@ActivityImpl(taskQueues = ["lending-task-queue"])
class LedgerActivitiesImpl(
    private val loanRepository: LoanRepository,
    private val ledgerEntryRepository: LedgerEntryRepository
) : LedgerActivities {

    @Transactional
    override fun recordDisbursement(loanId: Long, amount: BigDecimal) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        val runningBalance = loan.outstandingBalance
        ledgerEntryRepository.save(
            LedgerEntry(
                loan = loan,
                entryType = LedgerEntryType.DISBURSEMENT,
                amount = amount,
                runningBalance = runningBalance,
                description = "Loan disbursement"
            )
        )
    }

    @Transactional
    override fun recordUpfrontInterest(loanId: Long, amount: BigDecimal) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.accruedInterest += amount
        loan.outstandingBalance += amount
        loanRepository.save(loan)
        ledgerEntryRepository.save(
            LedgerEntry(
                loan = loan,
                entryType = LedgerEntryType.INTEREST_ACCRUAL,
                amount = amount,
                runningBalance = loan.outstandingBalance,
                description = "Upfront fixed interest"
            )
        )
    }

    @Transactional
    override fun recordDailyInterest(loanId: Long, dailyRate: BigDecimal) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        val principalBalance = loan.outstandingBalance - loan.accruedInterest - loan.lateFees
        val interestAmount = (principalBalance * dailyRate).setScale(2, RoundingMode.HALF_UP)
        if (interestAmount <= BigDecimal.ZERO) return

        loan.accruedInterest += interestAmount
        loan.outstandingBalance += interestAmount
        loanRepository.save(loan)

        ledgerEntryRepository.save(
            LedgerEntry(
                loan = loan,
                entryType = LedgerEntryType.INTEREST_ACCRUAL,
                amount = interestAmount,
                runningBalance = loan.outstandingBalance,
                description = "Daily interest accrual"
            )
        )
    }

    @Transactional
    override fun recordLateFee(loanId: Long, overdueAmount: BigDecimal, lateFeeRate: BigDecimal) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        val feeAmount = (overdueAmount * lateFeeRate).setScale(2, RoundingMode.HALF_UP)
        if (feeAmount <= BigDecimal.ZERO) return

        loan.lateFees += feeAmount
        loan.outstandingBalance += feeAmount
        loanRepository.save(loan)

        ledgerEntryRepository.save(
            LedgerEntry(
                loan = loan,
                entryType = LedgerEntryType.LATE_FEE,
                amount = feeAmount,
                runningBalance = loan.outstandingBalance,
                description = "Late payment fee"
            )
        )
    }

    @Transactional
    override fun recordPayment(loanId: Long, amount: BigDecimal, referenceId: String?) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        ledgerEntryRepository.save(
            LedgerEntry(
                loan = loan,
                entryType = LedgerEntryType.PAYMENT,
                amount = amount.negate(),
                runningBalance = loan.outstandingBalance,
                description = "Borrower payment",
                referenceId = referenceId
            )
        )
    }

    @Transactional(readOnly = true)
    override fun getOutstandingBalance(loanId: Long): BigDecimal {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        return loan.outstandingBalance
    }
}
