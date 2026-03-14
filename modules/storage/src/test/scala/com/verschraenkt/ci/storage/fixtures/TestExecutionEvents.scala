package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.tables.ExecutionEventRow
import io.circe.Json

import java.time.Instant
import java.util.UUID

/** Reusable test data for execution event tests */
object TestExecutionEvents:

  private val snowflakeProvider = SnowflakeProvider.make(74)
  private var counter           = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  private def nextId = snowflakeProvider.nextId().value

  def event(
      executionId: Long = nextId,
      workflowExecutionId: Long = 0L,
      jobExecutionId: Long = 0L,
      stepExecutionId: Long = 0L,
      eventType: String = "status_change",
      correlationId: UUID = UUID.randomUUID()
  ): ExecutionEventRow =
    ExecutionEventRow(
      eventId = None,
      eventUUID = None,
      executionId = executionId,
      workflowExecutionId = workflowExecutionId,
      jobExecutionId = jobExecutionId,
      stepExecutionId = stepExecutionId,
      eventType = eventType,
      eventPayload = Json.obj("message" -> Json.fromString(s"event-$getCounter")),
      occurredAt = Instant.now(),
      receivedAt = Instant.now(),
      correlationId = correlationId
    )

  def statusChangeEvent(
      executionId: Long = nextId,
      workflowExecutionId: Long = 0L,
      jobExecutionId: Long = 0L,
      stepExecutionId: Long = 0L,
      correlationId: UUID = UUID.randomUUID()
  ): ExecutionEventRow =
    event(
      executionId = executionId,
      workflowExecutionId = workflowExecutionId,
      jobExecutionId = jobExecutionId,
      stepExecutionId = stepExecutionId,
      eventType = "status_change",
      correlationId = correlationId
    )

  def logEvent(
      executionId: Long = nextId,
      workflowExecutionId: Long = 0L,
      jobExecutionId: Long = 0L,
      stepExecutionId: Long = 0L,
      correlationId: UUID = UUID.randomUUID()
  ): ExecutionEventRow =
    event(
      executionId = executionId,
      workflowExecutionId = workflowExecutionId,
      jobExecutionId = jobExecutionId,
      stepExecutionId = stepExecutionId,
      eventType = "log",
      correlationId = correlationId
    )

  def metricEvent(
      executionId: Long = nextId,
      workflowExecutionId: Long = 0L,
      jobExecutionId: Long = 0L,
      stepExecutionId: Long = 0L,
      correlationId: UUID = UUID.randomUUID()
  ): ExecutionEventRow =
    event(
      executionId = executionId,
      workflowExecutionId = workflowExecutionId,
      jobExecutionId = jobExecutionId,
      stepExecutionId = stepExecutionId,
      eventType = "metric",
      correlationId = correlationId
    )
