package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, TriggerType }
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.db.tables.ExecutionRow
import io.circe.Json

import java.time.Instant

/** Reusable test data for execution tests */
object TestExecutions:

  // Using this to get a separate id for each test execution
  private var counter = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  val testUser: User            = User("test-user")
  val anotherUser: User         = User("another-user")
  private val snowflakeProvider = SnowflakeProvider.make(68)

  private def getNextSnowflake = snowflakeProvider.nextId()

  /** Simple queued execution */
  def queuedExecution(pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter")): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Pending,
      trigger = TriggerType.Manual,
      triggerBy = testUser,
      triggerMetadata = None,
      idempotencyKey = None,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 0L,
      totalMemoryMibSeconds = 0L,
      labels = List.empty[String],
      errorMessage = None,
      deletedAt = None
    )

  /** Running execution */
  def runningExecution(pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter")): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Running,
      trigger = TriggerType.Manual,
      triggerBy = testUser,
      triggerMetadata = Some(Json.obj("source" -> Json.fromString("web-ui"))),
      idempotencyKey = None,
      queuedAt = Instant.now().minusSeconds(60),
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 15000L,
      totalMemoryMibSeconds = 512000L,
      labels = List("env:production"),
      errorMessage = None,
      deletedAt = None
    )

  /** Completed execution */
  def completedExecution(pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter")): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Completed,
      trigger = TriggerType.Schedule,
      triggerBy = User("scheduler"),
      triggerMetadata = Some(Json.obj("cron" -> Json.fromString("0 0 * * *"))),
      idempotencyKey = Some(s"idempotency-key-$getCounter"),
      queuedAt = Instant.now().minusSeconds(300),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = Some(Instant.now().plusSeconds(3000)),
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 120000L,
      totalMemoryMibSeconds = 2048000L,
      labels = List("env:production", "version:1.0.0"),
      errorMessage = None,
      deletedAt = None
    )

  /** Failed execution */
  def failedExecution(pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter")): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Failed,
      trigger = TriggerType.Webhook,
      triggerBy = User("github"),
      triggerMetadata = Some(Json.obj("commit" -> Json.fromString("abc123"))),
      idempotencyKey = None,
      queuedAt = Instant.now().minusSeconds(300),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      timeoutAt = Some(Instant.now().plusSeconds(3000)),
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 45000L,
      totalMemoryMibSeconds = 1024000L,
      labels = List("env:staging"),
      errorMessage = Some("Build failed: exit code 1"),
      deletedAt = None
    )

  /** Execution with concurrency control */
  def executionWithConcurrency(
      pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter"),
      group: String = "default-group",
      position: Int = 1
  ): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Pending,
      trigger = TriggerType.Manual,
      triggerBy = testUser,
      triggerMetadata = None,
      idempotencyKey = None,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = None,
      timeoutAt = Some(Instant.now().plusSeconds(3600)),
      concurrencyGroup = Some(group),
      concurrencyQueuePosition = Some(position),
      totalCpuMilliSeconds = 0L,
      totalMemoryMibSeconds = 0L,
      labels = List.empty[String],
      errorMessage = None,
      deletedAt = None
    )

  /** Timed out execution */
  def timedOutExecution(pipelineId: PipelineId = PipelineId(s"pipeline-$getCounter")): ExecutionRow =
    ExecutionRow(
      executionId = getNextSnowflake.value,
      pipelineId = pipelineId,
      pipelineVersion = 1,
      status = ExecutionStatus.Running,
      trigger = TriggerType.Manual,
      triggerBy = testUser,
      triggerMetadata = None,
      idempotencyKey = None,
      queuedAt = Instant.now().minusSeconds(7200),
      startedAt = Some(Instant.now().minusSeconds(7000)),
      completedAt = None,
      timeoutAt = Some(Instant.now().minusSeconds(10)), // Timed out 10 seconds ago
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 300000L,
      totalMemoryMibSeconds = 4096000L,
      labels = List.empty[String],
      errorMessage = None,
      deletedAt = None
    )

  /** Custom execution with specific ID */
  def withId(id: Long): ExecutionRow =
    queuedExecution().copy(executionId = id)

  /** Custom execution with specific status */
  def withStatus(status: ExecutionStatus): ExecutionRow =
    queuedExecution().copy(status = status)

  /** Custom execution with idempotency key */
  def withIdempotencyKey(key: String): ExecutionRow =
    queuedExecution().copy(idempotencyKey = Some(key))

  /** Custom execution with specific pipeline */
  def forPipeline(pipelineId: PipelineId): ExecutionRow =
    queuedExecution(pipelineId)
