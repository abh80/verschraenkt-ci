package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.fixtures.TestExecutionEvents
import io.circe.Json
import munit.FunSuite

import java.time.Instant
import java.util.UUID

class ExecutionEventTableSpec extends FunSuite:

  test("ExecutionEventRow creates correct row with all fields") {
    val row = TestExecutionEvents.statusChangeEvent()

    assertEquals(row.eventId, None)
    assertEquals(row.eventUUID, None)
    assertEquals(row.eventType, "status_change")
    assert(row.eventPayload.isObject)
    assert(row.workflowExecutionId >= 0L)
    assert(row.jobExecutionId >= 0L)
    assert(row.stepExecutionId >= 0L)
  }

  test("ExecutionEventRow handles log events") {
    val row = TestExecutionEvents.logEvent()

    assertEquals(row.eventType, "log")
    assert(row.eventPayload.hcursor.get[String]("message").toOption.exists(_.startsWith("event-")))
  }

  test("ExecutionEventRow handles metric events") {
    val row = TestExecutionEvents.metricEvent()

    assertEquals(row.eventType, "metric")
    assert(row.occurredAt.isBefore(row.receivedAt) || row.occurredAt.equals(row.receivedAt))
  }

  test("ExecutionEventRow stores parent execution references") {
    val row = TestExecutionEvents.event(
      executionId = 100L,
      workflowExecutionId = 101L,
      jobExecutionId = 102L,
      stepExecutionId = 103L
    )

    assertEquals(row.executionId, 100L)
    assertEquals(row.workflowExecutionId, 101L)
    assertEquals(row.jobExecutionId, 102L)
    assertEquals(row.stepExecutionId, 103L)
  }

  test("ExecutionEventRow stores correlation id correctly") {
    val correlationId = UUID.randomUUID()
    val row           = TestExecutionEvents.event(correlationId = correlationId)

    assertEquals(row.correlationId, correlationId)
  }

  test("ExecutionEventRow handles custom JSON payload") {
    val payload = Json.obj(
      "status"  -> Json.fromString("running"),
      "attempt" -> Json.fromInt(2)
    )
    val row = TestExecutionEvents.event().copy(eventPayload = payload)

    assert(row.eventPayload.isObject)
    assertEquals(row.eventPayload.hcursor.get[String]("status").toOption, Some("running"))
    assertEquals(row.eventPayload.hcursor.get[Int]("attempt").toOption, Some(2))
  }

  test("ExecutionEventRow default values for generated fields") {
    val row = ExecutionEventRow(
      eventId = None,
      eventUUID = None,
      executionId = 1L,
      workflowExecutionId = 0L,
      jobExecutionId = 0L,
      stepExecutionId = 0L,
      eventType = "status_change",
      eventPayload = Json.obj(),
      occurredAt = Instant.now(),
      receivedAt = Instant.now(),
      correlationId = UUID.randomUUID()
    )

    assertEquals(row.eventId, None)
    assertEquals(row.eventUUID, None)
    assertEquals(row.workflowExecutionId, 0L)
    assertEquals(row.jobExecutionId, 0L)
    assertEquals(row.stepExecutionId, 0L)
  }
