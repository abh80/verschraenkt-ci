package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.core.model.WorkflowId
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.WorkflowExecutionRow

import java.time.Instant

/** Reusable test data for workflow execution tests */
object TestWorkflowExecutions:

  private val snowflakeProvider = SnowflakeProvider.make(69)
  private var counter           = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  private def nextId = snowflakeProvider.nextId().value

  def queuedWorkflowExecution(
      executionId: Long = nextId,
      workflowId: WorkflowId = WorkflowId(s"workflow-$getCounter")
  ): WorkflowExecutionRow =
    WorkflowExecutionRow(
      workflowExecutionId = nextId,
      executionId = executionId,
      workflowId = workflowId,
      workflowName = "build",
      status = ExecutionStatus.Pending,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      conditionResult = None,
      conditionExpression = None,
      errorMessage = None
    )

  def runningWorkflowExecution(
      executionId: Long = nextId,
      workflowId: WorkflowId = WorkflowId(s"workflow-$getCounter")
  ): WorkflowExecutionRow =
    WorkflowExecutionRow(
      workflowExecutionId = nextId,
      executionId = executionId,
      workflowId = workflowId,
      workflowName = "build",
      status = ExecutionStatus.Running,
      queuedAt = Instant.now().minusSeconds(60),
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      conditionResult = Some(true),
      conditionExpression = Some("always()"),
      errorMessage = None
    )

  def completedWorkflowExecution(
      executionId: Long = nextId,
      workflowId: WorkflowId = WorkflowId(s"workflow-$getCounter")
  ): WorkflowExecutionRow =
    WorkflowExecutionRow(
      workflowExecutionId = nextId,
      executionId = executionId,
      workflowId = workflowId,
      workflowName = "deploy",
      status = ExecutionStatus.Completed,
      queuedAt = Instant.now().minusSeconds(300),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = Some(Instant.now().plusSeconds(3000)),
      conditionResult = Some(true),
      conditionExpression = Some("success()"),
      errorMessage = None
    )

  def failedWorkflowExecution(
      executionId: Long = nextId,
      workflowId: WorkflowId = WorkflowId(s"workflow-$getCounter")
  ): WorkflowExecutionRow =
    WorkflowExecutionRow(
      workflowExecutionId = nextId,
      executionId = executionId,
      workflowId = workflowId,
      workflowName = "test",
      status = ExecutionStatus.Failed,
      queuedAt = Instant.now().minusSeconds(300),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = None,
      conditionResult = Some(true),
      conditionExpression = None,
      errorMessage = Some("Workflow failed: job 'test' failed")
    )

  def skippedWorkflowExecution(
      executionId: Long = nextId,
      workflowId: WorkflowId = WorkflowId(s"workflow-$getCounter")
  ): WorkflowExecutionRow =
    WorkflowExecutionRow(
      workflowExecutionId = nextId,
      executionId = executionId,
      workflowId = workflowId,
      workflowName = "conditional",
      status = ExecutionStatus.Cancelled,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = Some(Instant.now()),
      timeoutAt = None,
      conditionResult = Some(false),
      conditionExpression = Some("branch == 'main'"),
      errorMessage = None
    )

  def withStatus(status: ExecutionStatus): WorkflowExecutionRow =
    queuedWorkflowExecution().copy(status = status)
