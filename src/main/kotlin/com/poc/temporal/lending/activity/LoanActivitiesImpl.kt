package com.poc.temporal.lending.activity

import com.poc.temporal.lending.domain.Payment
import com.poc.temporal.lending.domain.enums.LoanStatus
import com.poc.temporal.lending.repository.LoanRepository
import com.poc.temporal.lending.repository.PaymentRepository
import io.temporal.spring.boot.ActivityImpl
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
@ActivityImpl(taskQueues = ["lending-task-queue"])
class LoanActivitiesImpl(
    private val loanRepository: LoanRepository,
    private val paymentRepository: PaymentRepository
) : LoanActivities {

    @Transactional
    override fun disburseLoan(loanId: Long) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        val cycles = loan.product.numberOfPaymentCycles
        loan.status = LoanStatus.ACTIVE
        loan.disbursementDate = LocalDate.now()
        loan.maturityDate = LocalDate.now().plusMonths(cycles.toLong())
        loan.outstandingBalance = loan.principalAmount
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun applyPayment(loanId: Long, amount: BigDecimal, referenceNumber: String): BigDecimal {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }

        // Apply payment in order: late fees -> interest -> principal
        var remaining = amount
        val lateFeesApplied: BigDecimal
        val interestApplied: BigDecimal
        val principalApplied: BigDecimal

        if (remaining >= loan.lateFees) {
            lateFeesApplied = loan.lateFees
            remaining -= loan.lateFees
            loan.lateFees = BigDecimal.ZERO
        } else {
            lateFeesApplied = remaining
            loan.lateFees -= remaining
            remaining = BigDecimal.ZERO
        }

        if (remaining >= loan.accruedInterest) {
            interestApplied = loan.accruedInterest
            remaining -= loan.accruedInterest
            loan.accruedInterest = BigDecimal.ZERO
        } else {
            interestApplied = remaining
            loan.accruedInterest -= remaining
            remaining = BigDecimal.ZERO
        }

        val principalBalance = loan.outstandingBalance - loan.accruedInterest - loan.lateFees
        if (remaining >= principalBalance) {
            principalApplied = principalBalance
            remaining = BigDecimal.ZERO
        } else {
            principalApplied = remaining
            remaining = BigDecimal.ZERO
        }

        loan.outstandingBalance = maxOf(BigDecimal.ZERO, loan.outstandingBalance - principalApplied - interestApplied - lateFeesApplied)
        loan.totalPaid += amount
        loan.paymentsMade++
        loan.updatedAt = LocalDateTime.now()

        // If loan was delinquent and now has a payment, keep status check to workflow
        loanRepository.save(loan)

        paymentRepository.save(
            Payment(
                loan = loan,
                amount = amount,
                principalApplied = principalApplied,
                interestApplied = interestApplied,
                lateFeesApplied = lateFeesApplied,
                referenceNumber = referenceNumber,
                paymentDate = LocalDateTime.now()
            )
        )

        return loan.outstandingBalance
    }

    @Transactional
    override fun markDelinquent(loanId: Long) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.status = LoanStatus.DELINQUENT
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun markPaidOff(loanId: Long) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.status = LoanStatus.PAID_OFF
        loan.outstandingBalance = BigDecimal.ZERO
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun cancelLoan(loanId: Long, reason: String) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        if (loan.status != LoanStatus.PENDING && loan.status != LoanStatus.APPROVED) {
            throw IllegalStateException("Cannot cancel loan $loanId in status ${loan.status}")
        }
        loan.status = LoanStatus.CANCELLED
        loan.notes = reason
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun startCooldown(loanId: Long) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.status = LoanStatus.IN_COOLDOWN
        loan.cooldownEndDate = LocalDate.now().plusDays(loan.product.cooldownPeriodDays.toLong())
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun endCooldown(loanId: Long) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.status = LoanStatus.PAID_OFF
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }

    @Transactional
    override fun updateLoanStatus(loanId: Long, status: String) {
        val loan = loanRepository.findById(loanId).orElseThrow { IllegalArgumentException("Loan $loanId not found") }
        loan.status = LoanStatus.valueOf(status)
        loan.updatedAt = LocalDateTime.now()
        loanRepository.save(loan)
    }
}
