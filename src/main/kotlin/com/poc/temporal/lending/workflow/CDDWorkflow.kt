package com.poc.temporal.lending.workflow

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

/**
 * Workflow for the **CDD (Custom Due Date)** product: fixed upfront interest, single payment at maturity.
 *
 * Separating this from [InstallmentsWorkflow] allows both products to evolve independently —
 * maturity rules, upfront-fee logic, or grace-period behaviour can be changed here without
 * touching the installments logic.
 */
@WorkflowInterface
interface CDDWorkflow : LoanWorkflow {

    @WorkflowMethod
    fun execute(request: LoanWorkflowRequest)
}
