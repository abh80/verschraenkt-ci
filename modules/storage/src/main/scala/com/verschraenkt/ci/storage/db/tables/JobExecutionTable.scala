package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.JobId
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{ circeJsonTypeMapper, executionStatusMapper }
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import io.circe.Json

import java.time.Instant
import java.util.UUID

/** Represents a single persisted job execution row. The row tracks scheduling, execution windows, resource
  * requests/usage, retry state and terminal results for orchestration and auditing.
  *
  * @param jobExecutionId
  *   Primary key for this job execution attempt
  * @param workflowExecutionId
  *   Owning workflow execution identifier
  * @param executionId
  *   Denormalized pipeline execution identifier
  * @param jobId
  *   Logical job identifier from the pipeline spec
  * @param jobName
  *   Human-friendly job name
  * @param status
  *   Current execution status (pending, running, etc.)
  * @param dependencies
  *   JSON encoded upstream dependency graph
  * @param readyAt
  *   When all dependencies were satisfied
  * @param scheduledAt
  *   When the job was scheduled onto an executor
  * @param executorId
  *   UUID of the executor assigned to this job
  * @param assignedAt
  *   Timestamp for assignment to an executor
  * @param startedAt
  *   When the executor actually began running the job
  * @param completedAt
  *   When execution finished (success or failure)
  * @param timeoutAt
  *   Deadline after which the job should be aborted
  * @param requestedCpuMilli
  *   Requested CPU in milli-cores
  * @param requestedMemoryMib
  *   Requested memory in MiB
  * @param requestedGpu
  *   Requested GPU device count
  * @param requestedDiskMib
  *   Requested disk space in MiB
  * @param actualCpuMilliSeconds
  *   Actual CPU consumption in milli-seconds
  * @param actualMemoryMibSeconds
  *   Actual memory consumption in MiB-seconds
  * @param attemptNumber
  *   1-based attempt counter for retries
  * @param maxAttempts
  *   Maximum allowed attempts
  * @param exitCode
  *   Exit code emitted by the job process
  * @param errorMessage
  *   Error message when the job fails
  * @param matrixCoordinates
  *   JSON coordinates for matrix-expanded jobs
  */
final case class JobExecutionRow(
    jobExecutionId: Long,
    workflowExecutionId: Long,
    executionId: Long,
    jobId: JobId,
    jobName: String,
    status: ExecutionStatus,
    dependencies: Option[Json],
    readyAt: Option[Instant],
    scheduledAt: Option[Instant],
    executorId: Option[UUID],
    assignedAt: Option[Instant],
    startedAt: Option[Instant],
    completedAt: Option[Instant],
    timeoutAt: Option[Instant],
    requestedCpuMilli: Option[Int],
    requestedMemoryMib: Option[Int],
    requestedGpu: Option[Int],
    requestedDiskMib: Option[Int],
    actualCpuMilliSeconds: Option[Long],
    actualMemoryMibSeconds: Option[Long],
    attemptNumber: Int,
    maxAttempts: Int,
    exitCode: Option[Int],
    errorMessage: Option[String],
    matrixCoordinates: Option[Json]
)

class JobExecutionTable(tag: Tag) extends Table[JobExecutionRow](tag, "job_executions"):
  // -- Identification
  // Primary key identifying this job execution
  def jobExecutionId = column[Long]("job_execution_id", O.PrimaryKey)
  // Reference to the parent workflow execution
  def workflowExecutionId = column[Long]("workflow_execution_id")
  // Denormalized reference to the pipeline execution (avoids joining multiple tables)
  def executionId = column[Long]("execution_id")
  // Job identifier from the pipeline definition
  def jobId = column[JobId]("job_id")
  // Human-readable job name
  def jobName = column[String]("job_name")

  // -- Status tracking
  // Current execution status (pending, running, success, failure, etc.)
  def status = column[ExecutionStatus]("status")

  // -- Scheduling
  // Dependencies of this job in JSONB format (which jobs must complete first)
  def dependencies = column[Option[Json]]("dependencies")
  // When this job became ready to be scheduled (all dependencies satisfied)
  def readyAt = column[Option[Instant]]("ready_at")
  // When this job was scheduled to run on an executor
  def scheduledAt = column[Option[Instant]]("scheduled_at")

  // -- Execution
  // UUID of the executor assigned to run this job
  def executorId = column[Option[UUID]]("executor_id")
  // When this job was assigned to an executor
  def assignedAt = column[Option[Instant]]("assigned_at")
  // When execution of this job actually started
  def startedAt = column[Option[Instant]]("started_at")
  // When execution of this job completed (success or failure)
  def completedAt = column[Option[Instant]]("completed_at")
  // The deadline - if we exceed this, the job should be killed
  def timeoutAt = column[Option[Instant]]("timeout_at")

  // -- Requested resources
  // CPU requested in milliseconds
  def requestedCpuMilli = column[Option[Int]]("requested_cpu_milli")
  // Memory requested in MiB
  def requestedMemoryMib = column[Option[Int]]("requested_memory_mib")
  // GPU devices requested
  def requestedGpu = column[Option[Int]]("requested_gpu")
  // Disk space requested in MiB
  def requestedDiskMib = column[Option[Int]]("requested_disk_mib")

  // -- Actual resource usage
  // Actual CPU consumed in milli-seconds
  def actualCpuMilliSeconds = column[Option[Long]]("actual_cpu_milli_seconds")
  // Actual memory consumed in MiB-seconds
  def actualMemoryMibSeconds = column[Option[Long]]("actual_memory_mib_seconds")

  // -- Retry logic
  // Current attempt number (1-based, increments on retry)
  def attemptNumber = column[Int]("attempt_number")
  // Maximum number of attempts allowed for this job
  def maxAttempts = column[Int]("max_attempts")

  // -- Results
  // Exit code from the job (if applicable)
  def exitCode = column[Option[Int]]("exit_code")
  // Error message if the job failed
  def errorMessage = column[Option[String]]("error_message")

  // -- Matrix jobs
  // JSONB coordinates for matrix-expanded jobs
  def matrixCoordinates = column[Option[Json]]("matrix_coordinates")

  // Split into two groups to stay within tuple-22 arity limit
  private val coreCols = (
    jobExecutionId,
    workflowExecutionId,
    executionId,
    jobId,
    jobName,
    status,
    dependencies,
    readyAt,
    scheduledAt,
    executorId,
    assignedAt,
    startedAt,
    completedAt,
    timeoutAt
  )
  private val detailCols = (
    requestedCpuMilli,
    requestedMemoryMib,
    requestedGpu,
    requestedDiskMib,
    actualCpuMilliSeconds,
    actualMemoryMibSeconds,
    attemptNumber,
    maxAttempts,
    exitCode,
    errorMessage,
    matrixCoordinates
  )

  def * = (coreCols, detailCols).shaped <> ({ case (core, detail) =>
    JobExecutionRow(
      core._1,
      core._2,
      core._3,
      core._4,
      core._5,
      core._6,
      core._7,
      core._8,
      core._9,
      core._10,
      core._11,
      core._12,
      core._13,
      core._14,
      detail._1,
      detail._2,
      detail._3,
      detail._4,
      detail._5,
      detail._6,
      detail._7,
      detail._8,
      detail._9,
      detail._10,
      detail._11
    )
  }, { (r: JobExecutionRow) =>
    Some(
      (
        (
          r.jobExecutionId,
          r.workflowExecutionId,
          r.executionId,
          r.jobId,
          r.jobName,
          r.status,
          r.dependencies,
          r.readyAt,
          r.scheduledAt,
          r.executorId,
          r.assignedAt,
          r.startedAt,
          r.completedAt,
          r.timeoutAt
        ),
        (
          r.requestedCpuMilli,
          r.requestedMemoryMib,
          r.requestedGpu,
          r.requestedDiskMib,
          r.actualCpuMilliSeconds,
          r.actualMemoryMibSeconds,
          r.attemptNumber,
          r.maxAttempts,
          r.exitCode,
          r.errorMessage,
          r.matrixCoordinates
        )
      )
    )
  })

  def workflowExecutionFk = foreignKey(
    "job_executions_workflow_execution_id_fkey",
    workflowExecutionId,
    TableQuery[WorkflowExecutionTable]
  )(_.workflowExecutionId, onDelete = ForeignKeyAction.Cascade)

  def executorFk = foreignKey(
    "job_executions_executor_id_fkey",
    executorId,
    TableQuery[ExecutorTable]
  )(_.executorId.?, onDelete = ForeignKeyAction.SetNull)
