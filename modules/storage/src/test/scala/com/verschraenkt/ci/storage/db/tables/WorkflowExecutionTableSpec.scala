package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.WorkflowId
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.fixtures.TestWorkflowExecutions
import munit.FunSuite

import java.time.Instant

class WorkflowExecutionTableSpec extends FunSuite:

  test("WorkflowExecutionRow creates correct row with all fields") {
    val row = TestWorkflowExecutions.queuedWorkflowExecution()

    assertEquals(row.status, ExecutionStatus.Pending)
    assertEquals(row.workflowName, "build")
    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
    assertEquals(row.conditionResult, None)
    assertEquals(row.conditionExpression, None)
    assertEquals(row.errorMessage, None)
  }

  test("WorkflowExecutionRow handles running state") {
    val row = TestWorkflowExecutions.runningWorkflowExecution()

    assertEquals(row.status, ExecutionStatus.Running)
    assert(row.startedAt.isDefined)
    assertEquals(row.completedAt, None)
    assertEquals(row.conditionResult, Some(true))
    assertEquals(row.conditionExpression, Some("always()"))
  }

  test("WorkflowExecutionRow handles completed state") {
    val row = TestWorkflowExecutions.completedWorkflowExecution()

    assertEquals(row.status, ExecutionStatus.Completed)
    assert(row.startedAt.isDefined)
    assert(row.completedAt.isDefined)
    assert(row.completedAt.get.isAfter(row.startedAt.get))
  }

  test("WorkflowExecutionRow handles failed state with error message") {
    val row = TestWorkflowExecutions.failedWorkflowExecution()

    assertEquals(row.status, ExecutionStatus.Failed)
    assert(row.errorMessage.isDefined)
    assertEquals(row.errorMessage.get, "Workflow failed: job 'test' failed")
    assert(row.completedAt.isDefined)
  }

  test("WorkflowExecutionRow handles skipped workflow with false condition") {
    val row = TestWorkflowExecutions.skippedWorkflowExecution()

    assertEquals(row.status, ExecutionStatus.Cancelled)
    assertEquals(row.conditionResult, Some(false))
    assertEquals(row.conditionExpression, Some("branch == 'main'"))
    assertEquals(row.startedAt, None)
  }

  test("WorkflowExecutionRow stores WorkflowId correctly") {
    val workflowId = WorkflowId("custom-workflow-id")
    val row        = TestWorkflowExecutions.queuedWorkflowExecution(workflowId = workflowId)

    assertEquals(row.workflowId, workflowId)
  }

  test("WorkflowExecutionRow handles different execution statuses") {
    val pending   = TestWorkflowExecutions.withStatus(ExecutionStatus.Pending)
    val running   = TestWorkflowExecutions.withStatus(ExecutionStatus.Running)
    val completed = TestWorkflowExecutions.withStatus(ExecutionStatus.Completed)
    val failed    = TestWorkflowExecutions.withStatus(ExecutionStatus.Failed)

    assertEquals(pending.status, ExecutionStatus.Pending)
    assertEquals(running.status, ExecutionStatus.Running)
    assertEquals(completed.status, ExecutionStatus.Completed)
    assertEquals(failed.status, ExecutionStatus.Failed)
  }

  test("WorkflowExecutionRow handles timeout field") {
    val withTimeout    = TestWorkflowExecutions.queuedWorkflowExecution()
    val withoutTimeout = TestWorkflowExecutions.failedWorkflowExecution()

    assert(withTimeout.timeoutAt.isDefined)
    assertEquals(withoutTimeout.timeoutAt, None)
  }

  test("WorkflowExecutionRow queued has no start or completion time") {
    val row = TestWorkflowExecutions.queuedWorkflowExecution()

    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
  }

  test("WorkflowExecutionRow completed has both start and completion times") {
    val row = TestWorkflowExecutions.completedWorkflowExecution()

    assert(row.startedAt.isDefined)
    assert(row.completedAt.isDefined)
    assert(row.completedAt.get.isAfter(row.startedAt.get))
  }

  test("WorkflowExecutionRow default values for optional fields") {
    val row = WorkflowExecutionRow(
      workflowExecutionId = 1L,
      executionId = 1L,
      workflowId = WorkflowId("test"),
      workflowName = "test",
      status = ExecutionStatus.Pending,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = None,
      timeoutAt = None,
      conditionResult = None,
      conditionExpression = None,
      errorMessage = None
    )

    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
    assertEquals(row.timeoutAt, None)
    assertEquals(row.conditionResult, None)
    assertEquals(row.conditionExpression, None)
    assertEquals(row.errorMessage, None)
  }
