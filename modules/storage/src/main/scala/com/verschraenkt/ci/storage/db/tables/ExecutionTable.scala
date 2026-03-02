package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{ circeJsonTypeMapper, simpleStrListTypeMapper, triggerTypeMapper, executionStatusMapper }
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, TriggerType }
import com.verschraenkt.ci.storage.db.codecs.User
import io.circe.Json

import java.time.Instant
import java.util.UUID

/** Database row representation of an Execution
  *
  * @param executionId
  *   Unique time-ordered UUID for this execution instance
  * @param pipelineId
  *   Which pipeline is being executed
  * @param pipelineVersion
  *   Which version of the pipeline definition
  * @param status
  *   Current execution state
  * @param trigger
  *   How this execution was triggered
  * @param triggerBy
  *   Who/what triggered this execution
  * @param triggerMetadata
  *   Additional JSON context about the trigger
  * @param idempotencyKey
  *   Prevents duplicate executions
  * @param queuedAt
  *   When this execution entered the queue
  * @param startedAt
  *   When execution actually began
  * @param completedAt
  *   When execution finished
  * @param timeoutAt
  *   Kill deadline - execution terminated if past this
  * @param concurrencyGroup
  *   Groups executions that share concurrency limits
  * @param concurrencyQueuePosition
  *   Position in the concurrency queue
  * @param totalCpuMilliSeconds
  *   Total CPU consumed across all jobs
  * @param totalMemoryMibSeconds
  *   Total memory consumed across all jobs
  * @param labels
  *   Labels for filtering/organizing
  * @param errorMessage
  *   Error details if execution failed
  * @param deletedAt
  *   Soft-delete timestamp
  */
final case class ExecutionRow(
    executionId: UUID,
    pipelineId: PipelineId,
    pipelineVersion: Int,
    status: ExecutionStatus,
    trigger: TriggerType,
    triggerBy: User,
    triggerMetadata: Option[Json],
    idempotencyKey: Option[String],
    queuedAt: Instant,
    startedAt: Option[Instant],
    completedAt: Option[Instant],
    timeoutAt: Option[Instant],
    concurrencyGroup: Option[String],
    concurrencyQueuePosition: Option[Int],
    totalCpuMilliSeconds: Long,
    totalMemoryMibSeconds: Long,
    labels: List[String],
    errorMessage: Option[String],
    deletedAt: Option[Instant]
)

class ExecutionTable(tag: Tag) extends Table[ExecutionRow](tag, "executions"):
  /** Primary key - auto-generated UUID v7 (time-ordered) */
  def executionId = column[UUID]("execution_id", O.PrimaryKey)

  /** Pipeline identifier */
  def pipelineId = column[PipelineId]("pipeline_id")

  /** Pipeline version number */
  def pipelineVersion = column[Int]("pipeline_version")

  /** Execution status enum */
  def status = column[ExecutionStatus]("status")

  /** Trigger type enum */
  def trigger = column[TriggerType]("trigger")

  /** Who/what triggered this execution */
  def triggerBy = column[User]("trigger_by")

  /** Additional trigger context */
  def triggerMetadata = column[Option[Json]]("trigger_metadata")

  /** Idempotency key to prevent duplicate runs */
  def idempotencyKey = column[Option[String]]("idempotency_key")

  /** When queued (auto-set by DB) */
  def queuedAt = column[Instant]("queued_at")

  /** When execution started */
  def startedAt = column[Option[Instant]]("started_at")

  /** When execution completed */
  def completedAt = column[Option[Instant]]("completed_at")

  /** Execution deadline (kill if exceeded) */
  def timeoutAt = column[Option[Instant]]("timeout_at")

  /** Concurrency control group */
  def concurrencyGroup = column[Option[String]]("concurrency_group")

  /** Position in concurrency queue */
  def concurrencyQueuePosition = column[Option[Int]]("concurrency_queue_position")

  /** Total CPU consumed (milli-CPU-seconds) */
  def totalCpuMilliSeconds = column[Long]("total_cpu_milli_seconds")

  /** Total memory consumed (MiB-seconds) */
  def totalMemoryMibSeconds = column[Long]("total_memory_mib_seconds")

  /** TEXT[] labels */
  def labels = column[List[String]]("labels")

  /** Error message if failed */
  def errorMessage = column[Option[String]]("error_message")

  /** Soft-delete timestamp */
  def deletedAt = column[Option[Instant]]("deleted_at")

  def * = (
    executionId,
    pipelineId,
    pipelineVersion,
    status,
    trigger,
    triggerBy,
    triggerMetadata,
    idempotencyKey,
    queuedAt,
    startedAt,
    completedAt,
    timeoutAt,
    concurrencyGroup,
    concurrencyQueuePosition,
    totalCpuMilliSeconds,
    totalMemoryMibSeconds,
    labels,
    errorMessage,
    deletedAt
  ) <> ((ExecutionRow.apply).tupled, ExecutionRow.unapply)

  def pipelineVersionFk = foreignKey(
    "executions_pipeline_version_fkey",
    (pipelineId, pipelineVersion),
    TableQuery[PipelineVersionsTable]
  )(pv => (pv.pipelineId, pv.version), onDelete = ForeignKeyAction.Restrict)
