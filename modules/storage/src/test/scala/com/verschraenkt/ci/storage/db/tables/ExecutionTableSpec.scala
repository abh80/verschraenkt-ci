package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, TriggerType }
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.fixtures.TestExecutions
import munit.FunSuite

import java.time.Instant

class ExecutionTableSpec extends FunSuite:
  private val snowflakeProvider = SnowflakeProvider.make(67)

  test("ExecutionRow creates correct row with all fields") {
    val execution = TestExecutions.queuedExecution()

    assertEquals(execution.status, ExecutionStatus.Pending)
    assertEquals(execution.trigger, TriggerType.Manual)
    assertEquals(execution.triggerBy, TestExecutions.testUser)
    assertEquals(execution.triggerMetadata, None)
    assertEquals(execution.idempotencyKey, None)
    assertEquals(execution.startedAt, None)
    assertEquals(execution.completedAt, None)
    assertEquals(execution.errorMessage, None)
    assertEquals(execution.deletedAt, None)
  }

  test("ExecutionRow handles running execution state") {
    val execution = TestExecutions.runningExecution()

    assertEquals(execution.status, ExecutionStatus.Running)
    assert(execution.startedAt.isDefined)
    assert(execution.completedAt.isEmpty)
    assert(execution.totalCpuMilliSeconds > 0)
    assert(execution.totalMemoryMibSeconds > 0)
  }

  test("ExecutionRow handles completed execution state") {
    val execution = TestExecutions.completedExecution()

    assertEquals(execution.status, ExecutionStatus.Completed)
    assert(execution.startedAt.isDefined)
    assert(execution.completedAt.isDefined)
    assert(execution.idempotencyKey.isDefined)
    assert(execution.totalCpuMilliSeconds > 0)
  }

  test("ExecutionRow handles failed execution with error message") {
    val execution = TestExecutions.failedExecution()

    assertEquals(execution.status, ExecutionStatus.Failed)
    assert(execution.errorMessage.isDefined)
    assertEquals(execution.errorMessage.get, "Build failed: exit code 1")
    assert(execution.completedAt.isDefined)
  }

  test("ExecutionRow handles concurrency fields correctly") {
    val execution = TestExecutions.executionWithConcurrency(group = "test-group", position = 5)

    assertEquals(execution.concurrencyGroup, Some("test-group"))
    assertEquals(execution.concurrencyQueuePosition, Some(5))
  }

  test("ExecutionRow handles trigger metadata as JSON") {
    val execution = TestExecutions.runningExecution()

    assert(execution.triggerMetadata.isDefined)
    val metadata = execution.triggerMetadata.get
    assert(metadata.isObject)
  }

  test("ExecutionRow handles labels as Seq[String]") {
    val execution = TestExecutions.completedExecution()

    assert(execution.labels.isInstanceOf[Seq[String]])
  }

  test("ExecutionRow stores Snowflake correctly") {
    val customId  = snowflakeProvider.nextId().value
    val execution = TestExecutions.withId(customId)

    assertEquals(execution.executionId, customId)
  }

  test("ExecutionRow stores PipelineId correctly") {
    val pipelineId = PipelineId("test-pipeline-123")
    val execution  = TestExecutions.forPipeline(pipelineId)

    assertEquals(execution.pipelineId, pipelineId)
  }

  test("ExecutionRow handles different trigger types") {
    val manual    = TestExecutions.queuedExecution()
    val scheduled = TestExecutions.completedExecution()
    val webhook   = TestExecutions.failedExecution()

    assertEquals(manual.trigger, TriggerType.Manual)
    assertEquals(scheduled.trigger, TriggerType.Schedule)
    assertEquals(webhook.trigger, TriggerType.Webhook)
  }

  test("ExecutionRow handles different execution statuses") {
    val pending   = TestExecutions.withStatus(ExecutionStatus.Pending)
    val running   = TestExecutions.withStatus(ExecutionStatus.Running)
    val completed = TestExecutions.withStatus(ExecutionStatus.Completed)
    val failed    = TestExecutions.withStatus(ExecutionStatus.Failed)

    assertEquals(pending.status, ExecutionStatus.Pending)
    assertEquals(running.status, ExecutionStatus.Running)
    assertEquals(completed.status, ExecutionStatus.Completed)
    assertEquals(failed.status, ExecutionStatus.Failed)
  }

  test("ExecutionRow handles idempotency key") {
    val key       = "unique-key-123"
    val execution = TestExecutions.withIdempotencyKey(key)

    assertEquals(execution.idempotencyKey, Some(key))
  }

  test("ExecutionRow handles timeout correctly") {
    val execution = TestExecutions.timedOutExecution()

    assert(execution.timeoutAt.isDefined)
    assert(execution.timeoutAt.get.isBefore(Instant.now()))
  }

  test("ExecutionRow handles resource usage tracking") {
    val execution = TestExecutions.completedExecution()

    assert(execution.totalCpuMilliSeconds >= 0)
    assert(execution.totalMemoryMibSeconds >= 0)
  }

  test("ExecutionRow handles different users") {
    val user1      = User("user1")
    val user2      = User("user2")
    val execution1 = TestExecutions.queuedExecution().copy(triggerBy = user1)
    val execution2 = TestExecutions.queuedExecution().copy(triggerBy = user2)

    assertEquals(execution1.triggerBy, user1)
    assertEquals(execution2.triggerBy, user2)
  }

  test("ExecutionRow handles pipeline versions") {
    val execution = TestExecutions.queuedExecution().copy(pipelineVersion = 5)

    assertEquals(execution.pipelineVersion, 5)
  }

  test("ExecutionRow queued execution has no start or completion time") {
    val execution = TestExecutions.queuedExecution()

    assertEquals(execution.startedAt, None)
    assertEquals(execution.completedAt, None)
  }

  test("ExecutionRow running execution has start time but no completion time") {
    val execution = TestExecutions.runningExecution()

    assert(execution.startedAt.isDefined)
    assertEquals(execution.completedAt, None)
  }

  test("ExecutionRow completed execution has both start and completion times") {
    val execution = TestExecutions.completedExecution()

    assert(execution.startedAt.isDefined)
    assert(execution.completedAt.isDefined)
    assert(execution.completedAt.get.isAfter(execution.startedAt.get))
  }

  test("ExecutionRow handles empty labels") {
    val execution = TestExecutions.queuedExecution()

    assertEquals(execution.labels, Seq.empty[String])
  }

  test("ExecutionRow handles multiple label fields") {
    val labels    = List("env:production", "version:1.0.0", "region:us-east-1")
    val execution = TestExecutions.queuedExecution().copy(labels = labels)

    assertEquals(execution.labels, labels)
  }

  test("ExecutionRow soft delete handling") {
    val execution = TestExecutions.queuedExecution().copy(deletedAt = Some(Instant.now()))

    assert(execution.deletedAt.isDefined)
  }

  test("ExecutionRow default values for optional fields") {
    val execution = ExecutionRow(
      executionId = snowflakeProvider.tryNextId().toOption.get.value,
      pipelineId = PipelineId("test"),
      pipelineVersion = 1,
      status = ExecutionStatus.Pending,
      trigger = TriggerType.Manual,
      triggerBy = User("test"),
      triggerMetadata = None,
      idempotencyKey = None,
      queuedAt = Instant.now(),
      startedAt = None,
      completedAt = None,
      timeoutAt = None,
      concurrencyGroup = None,
      concurrencyQueuePosition = None,
      totalCpuMilliSeconds = 0L,
      totalMemoryMibSeconds = 0L,
      labels = List.empty[String],
      errorMessage = None,
      deletedAt = None
    )

    assertEquals(execution.triggerMetadata, None)
    assertEquals(execution.idempotencyKey, None)
    assertEquals(execution.startedAt, None)
    assertEquals(execution.completedAt, None)
    assertEquals(execution.timeoutAt, None)
    assertEquals(execution.concurrencyGroup, None)
    assertEquals(execution.concurrencyQueuePosition, None)
    assertEquals(execution.errorMessage, None)
    assertEquals(execution.deletedAt, None)
  }
