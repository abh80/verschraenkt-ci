package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.core.model.JobId
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.JobExecutionRow
import io.circe.Json

import java.time.Instant
import java.util.UUID

/** Reusable test data for job execution tests */
object TestJobExecutions:

  private val snowflakeProvider = SnowflakeProvider.make(70)
  private var counter           = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  private def nextId = snowflakeProvider.nextId().value

  def queuedJobExecution(
      workflowExecutionId: Long = nextId,
      executionId: Long = nextId,
      jobId: JobId = JobId(s"job-$getCounter")
  ): JobExecutionRow =
    JobExecutionRow(
      jobExecutionId = nextId,
      workflowExecutionId = workflowExecutionId,
      executionId = executionId,
      jobId = jobId,
      jobName = "build",
      status = ExecutionStatus.Pending,
      dependencies = None,
      readyAt = None,
      scheduledAt = None,
      executorId = None,
      assignedAt = None,
      startedAt = None,
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      requestedCpuMilli = Some(1000),
      requestedMemoryMib = Some(2048),
      requestedGpu = None,
      requestedDiskMib = Some(10240),
      actualCpuMilliSeconds = None,
      actualMemoryMibSeconds = None,
      attemptNumber = 1,
      maxAttempts = 3,
      exitCode = None,
      errorMessage = None,
      matrixCoordinates = None
    )

  def runningJobExecution(
      workflowExecutionId: Long = nextId,
      executionId: Long = nextId,
      jobId: JobId = JobId(s"job-$getCounter")
  ): JobExecutionRow =
    val executorUuid = UUID.randomUUID()
    JobExecutionRow(
      jobExecutionId = nextId,
      workflowExecutionId = workflowExecutionId,
      executionId = executionId,
      jobId = jobId,
      jobName = "test",
      status = ExecutionStatus.Running,
      dependencies = Some(Json.arr(Json.fromString("build"))),
      readyAt = Some(Instant.now().minusSeconds(120)),
      scheduledAt = Some(Instant.now().minusSeconds(90)),
      executorId = Some(executorUuid),
      assignedAt = Some(Instant.now().minusSeconds(60)),
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      requestedCpuMilli = Some(2000),
      requestedMemoryMib = Some(4096),
      requestedGpu = Some(1),
      requestedDiskMib = Some(20480),
      actualCpuMilliSeconds = Some(15000L),
      actualMemoryMibSeconds = Some(512000L),
      attemptNumber = 1,
      maxAttempts = 3,
      exitCode = None,
      errorMessage = None,
      matrixCoordinates = None
    )

  def completedJobExecution(
      workflowExecutionId: Long = nextId,
      executionId: Long = nextId,
      jobId: JobId = JobId(s"job-$getCounter")
  ): JobExecutionRow =
    JobExecutionRow(
      jobExecutionId = nextId,
      workflowExecutionId = workflowExecutionId,
      executionId = executionId,
      jobId = jobId,
      jobName = "deploy",
      status = ExecutionStatus.Completed,
      dependencies = Some(Json.arr(Json.fromString("build"), Json.fromString("test"))),
      readyAt = Some(Instant.now().minusSeconds(300)),
      scheduledAt = Some(Instant.now().minusSeconds(280)),
      executorId = Some(UUID.randomUUID()),
      assignedAt = Some(Instant.now().minusSeconds(270)),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = Some(Instant.now().plusSeconds(3000)),
      requestedCpuMilli = Some(1000),
      requestedMemoryMib = Some(2048),
      requestedGpu = None,
      requestedDiskMib = Some(10240),
      actualCpuMilliSeconds = Some(120000L),
      actualMemoryMibSeconds = Some(2048000L),
      attemptNumber = 1,
      maxAttempts = 3,
      exitCode = Some(0),
      errorMessage = None,
      matrixCoordinates = None
    )

  def failedJobExecution(
      workflowExecutionId: Long = nextId,
      executionId: Long = nextId,
      jobId: JobId = JobId(s"job-$getCounter")
  ): JobExecutionRow =
    JobExecutionRow(
      jobExecutionId = nextId,
      workflowExecutionId = workflowExecutionId,
      executionId = executionId,
      jobId = jobId,
      jobName = "lint",
      status = ExecutionStatus.Failed,
      dependencies = None,
      readyAt = Some(Instant.now().minusSeconds(300)),
      scheduledAt = Some(Instant.now().minusSeconds(280)),
      executorId = Some(UUID.randomUUID()),
      assignedAt = Some(Instant.now().minusSeconds(270)),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = None,
      requestedCpuMilli = Some(500),
      requestedMemoryMib = Some(1024),
      requestedGpu = None,
      requestedDiskMib = None,
      actualCpuMilliSeconds = Some(45000L),
      actualMemoryMibSeconds = Some(1024000L),
      attemptNumber = 3,
      maxAttempts = 3,
      exitCode = Some(1),
      errorMessage = Some("Lint check failed: 5 errors found"),
      matrixCoordinates = None
    )

  def matrixJobExecution(
      workflowExecutionId: Long = nextId,
      executionId: Long = nextId,
      jobId: JobId = JobId(s"job-$getCounter")
  ): JobExecutionRow =
    JobExecutionRow(
      jobExecutionId = nextId,
      workflowExecutionId = workflowExecutionId,
      executionId = executionId,
      jobId = jobId,
      jobName = "test-matrix",
      status = ExecutionStatus.Running,
      dependencies = None,
      readyAt = Some(Instant.now().minusSeconds(60)),
      scheduledAt = Some(Instant.now().minusSeconds(50)),
      executorId = Some(UUID.randomUUID()),
      assignedAt = Some(Instant.now().minusSeconds(40)),
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      requestedCpuMilli = Some(1000),
      requestedMemoryMib = Some(2048),
      requestedGpu = None,
      requestedDiskMib = Some(10240),
      actualCpuMilliSeconds = Some(10000L),
      actualMemoryMibSeconds = Some(256000L),
      attemptNumber = 1,
      maxAttempts = 1,
      exitCode = None,
      errorMessage = None,
      matrixCoordinates = Some(Json.obj(
        "os"      -> Json.fromString("ubuntu-22.04"),
        "scala"   -> Json.fromString("3.3.1"),
        "jdk"     -> Json.fromInt(17)
      ))
    )

  def withStatus(status: ExecutionStatus): JobExecutionRow =
    queuedJobExecution().copy(status = status)
