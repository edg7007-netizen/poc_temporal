package com.poc.temporal.lending.workflow

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Workflow for the **Term Loan** product: 12 monthly payment cycles with daily interest accrual.
 *
 * Separating this from [BulletLoanWorkflow] allows both products to evolve independently —
 * new cycles, grace-period rules, or late-fee policies can be changed here without touching
 * the bullet-loan logic.
 */
@WorkflowInterface
interface TermLoanWorkflow : LoanWorkflow {

    @WorkflowMethod
    fun execute(request: LoanWorkflowRequest)
}
