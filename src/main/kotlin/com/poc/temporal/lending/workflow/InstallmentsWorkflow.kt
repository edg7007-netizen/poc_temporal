package com.poc.temporal.lending.workflow

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Workflow for the **Installments** product: fixed monthly payment cycles with daily interest accrual.
 *
 * Separating this from [CDDWorkflow] allows both products to evolve independently —
 * new cycles, grace-period rules, or late-fee policies can be changed here without touching
 * the CDD logic.
 */
@WorkflowInterface
interface InstallmentsWorkflow : LoanWorkflow {

    @WorkflowMethod
    fun execute(request: LoanWorkflowRequest)
}
