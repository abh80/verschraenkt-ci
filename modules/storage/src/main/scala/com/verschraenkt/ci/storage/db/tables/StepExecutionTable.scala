package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.StepId
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{
  executionStatusMapper,
  stepTypeMapper
}
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, StepType }

import java.time.Instant

/**
 * Represents a single step execution entry within {@code step_executions},
 * storing ordering, command metadata, runtime status and IO locations.
 *
 * @param stepExecutionId Primary key identifying this executed step
 * @param jobExecutionId  Owning job execution identifier
 * @param stepId          Logical step identifier from the workflow definition
 * @param stepKind        Type of step (run, uses, etc.)
 * @param stepIndex       0-based ordering within the job
 * @param status          Current execution status for the step
 * @param startedAt       Timestamp when the step began running
 * @param completedAt     Timestamp when execution finished
 * @param commandText     Rendered command/script text sent to the executor
 * @param shell           Shell used to execute the command
 * @param exitCode        Process exit code if the step executed
 * @param stdoutLocation  Storage URI for captured stdout
 * @param stderrLocation  Storage URI for captured stderr
 * @param errorMessage    Error/diagnostic message on failure
 * @param continueOnError Whether the workflow should continue despite failure
 */
final case class StepExecutionRow(
    stepExecutionId: Long,
    jobExecutionId: Long,
    stepId: StepId,
    stepKind: StepType,
    stepIndex: Int,
    status: ExecutionStatus,
    startedAt: Option[Instant],
    completedAt: Option[Instant],
    commandText: Option[String],
    shell: Option[String],
    exitCode: Option[Int],
    stdoutLocation: Option[String],
    stderrLocation: Option[String],
    errorMessage: Option[String],
    continueOnError: Boolean
)

class StepExecutionTable(tag: Tag) extends Table[StepExecutionRow](tag, "step_executions"):
  def stepExecutionId = column[Long]("step_execution_id", O.PrimaryKey)
  def jobExecutionId  = column[Long]("job_execution_id")
  def stepId          = column[StepId]("step_id")
  def stepKind        = column[StepType]("step_kind")
  def stepIndex       = column[Int]("step_index")
  def status          = column[ExecutionStatus]("status")
  def startedAt       = column[Option[Instant]]("started_at")
  def completedAt     = column[Option[Instant]]("completed_at")
  def commandText     = column[Option[String]]("command_text")
  def shell           = column[Option[String]]("shell")
  def exitCode        = column[Option[Int]]("exit_code")
  def stdoutLocation  = column[Option[String]]("stdout_location")
  def stderrLocation  = column[Option[String]]("stderr_location")
  def errorMessage    = column[Option[String]]("error_message")
  def continueOnError = column[Boolean]("continue_on_error")

  def * = (
    stepExecutionId,
    jobExecutionId,
    stepId,
    stepKind,
    stepIndex,
    status,
    startedAt,
    completedAt,
    commandText,
    shell,
    exitCode,
    stdoutLocation,
    stderrLocation,
    errorMessage,
    continueOnError
  ) <> ((StepExecutionRow.apply).tupled, StepExecutionRow.unapply)

  def jobExecutionFk = foreignKey(
    "step_executions_job_execution_id_fkey",
    jobExecutionId,
    TableQuery[JobExecutionTable]
  )(_.jobExecutionId, onDelete = ForeignKeyAction.Cascade)

  def uniqueStepOrder = index(
    "unique_step_order",
    (jobExecutionId, stepIndex),
    unique = true
  )
