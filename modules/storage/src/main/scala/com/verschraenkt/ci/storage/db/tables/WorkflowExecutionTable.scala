package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.WorkflowId
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.executionStatusMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus

import java.time.Instant

/**
 * Captures a workflow execution persisted in {@code workflow_executions},
 * covering queue/start/completion windows, conditional evaluation and failures.
 *
 * @param workflowExecutionId Primary key for this workflow execution instance
 * @param executionId         Denormalized pipeline execution identifier
 * @param workflowId          Identifier of the workflow definition
 * @param workflowName        Human-readable name for observability
 * @param status              Current execution status
 * @param queuedAt            Timestamp when the workflow entered the queue
 * @param startedAt           When execution actually began
 * @param completedAt         When execution finished (success/failure)
 * @param timeoutAt           Deadline after which execution is considered expired
 * @param conditionResult     Result of conditional workflow evaluation (if any)
 * @param conditionExpression Condition expression evaluated for branching
 * @param errorMessage        Failure details when execution ends in error
 */
final case class WorkflowExecutionRow(
    workflowExecutionId: Long,
    executionId: Long,
    workflowId: WorkflowId,
    workflowName: String,
    status: ExecutionStatus,
    queuedAt: Instant,
    startedAt: Option[Instant],
    completedAt: Option[Instant],
    timeoutAt: Option[Instant],
    conditionResult: Option[Boolean],
    conditionExpression: Option[String],
    errorMessage: Option[String]
)

class WorkflowExecutionTable(tag: Tag) extends Table[WorkflowExecutionRow](tag, "workflow_executions"):
  def workflowExecutionId = column[Long]("workflow_execution_id", O.PrimaryKey)
  def executionId         = column[Long]("execution_id")
  def workflowId          = column[WorkflowId]("workflow_id")
  def workflowName        = column[String]("workflow_name")
  def status              = column[ExecutionStatus]("status")
  def queuedAt            = column[Instant]("queued_at")
  def startedAt           = column[Option[Instant]]("started_at")
  def completedAt         = column[Option[Instant]]("completed_at")
  def timeoutAt           = column[Option[Instant]]("timeout_at")
  def conditionResult     = column[Option[Boolean]]("condition_result")
  def conditionExpression = column[Option[String]]("condition_expression")
  def errorMessage        = column[Option[String]]("error_message")

  def * = (
    workflowExecutionId,
    executionId,
    workflowId,
    workflowName,
    status,
    queuedAt,
    startedAt,
    completedAt,
    timeoutAt,
    conditionResult,
    conditionExpression,
    errorMessage
  ) <> ((WorkflowExecutionRow.apply).tupled, WorkflowExecutionRow.unapply)

  def executionFk = foreignKey(
    "workflow_executions_execution_id_fkey",
    executionId,
    TableQuery[ExecutionTable]
  )(_.executionId, onDelete = ForeignKeyAction.Cascade)
