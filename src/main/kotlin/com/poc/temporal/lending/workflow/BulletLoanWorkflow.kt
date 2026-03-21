package com.poc.temporal.lending.workflow

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Workflow for the **Bullet Loan** product: fixed upfront interest, single payment at maturity.
 *
 * Separating this from [TermLoanWorkflow] allows both products to evolve independently —
 * maturity rules, upfront-fee logic, or grace-period behaviour can be changed here without
 * touching the term-loan logic.
 */
@WorkflowInterface
interface BulletLoanWorkflow : LoanWorkflow {

    @WorkflowMethod
    fun execute(request: LoanWorkflowRequest)
}
