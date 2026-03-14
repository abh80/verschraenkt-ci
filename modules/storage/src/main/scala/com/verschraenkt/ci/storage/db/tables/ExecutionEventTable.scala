package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.circeJsonTypeMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import io.circe.Json
import slick.model.ForeignKeyAction.Cascade

import java.time.Instant
import java.util.UUID

/** Database row representation of an ExecutionEvent
  *
  * @param eventId
  *   Auto-generated sequential ID (part of composite PK with occurredAt)
  * @param eventUUID
  *   Time-ordered UUID v7, DB-generated - used as external identifier
  * @param executionId
  *   Parent execution this event belongs to
  * @param workflowExecutionId
  *   Associated workflow execution, if applicable
  * @param jobExecutionId
  *   Associated job execution, if applicable
  * @param stepExecutionId
  *   Associated step execution, if applicable
  * @param eventType
  *   Event type discriminator (raw string for extensibility)
  * @param eventPayload
  *   Structured event data as JSONB
  * @param occurredAt
  *   When the event actually happened (partition key)
  * @param receivedAt
  *   When the system received/recorded the event
  * @param correlationId
  *   Optional correlation ID for tracing related events
  */
case class ExecutionEventRow(
    eventId: Option[Long],
    eventUUID: Option[UUID],
    executionId: Long,
    workflowExecutionId: Long,
    jobExecutionId: Long,
    stepExecutionId: Long,
    eventType: String,
    eventPayload: Json,
    occurredAt: Instant,
    receivedAt: Instant,
    correlationId: UUID
)

class ExecutionEventTable(tag: Tag) extends Table[ExecutionEventRow](tag, "execution_events"):
  def eventId             = column[Long]("event_id", O.AutoInc)
  def eventUUID           = column[UUID]("event_uuid", O.AutoInc)
  def executionId         = column[Long]("execution_id")
  def workflowExecutionId = column[Long]("workflow_execution_id")
  def jobExecutionId      = column[Long]("job_execution_id")
  def stepExecutionId     = column[Long]("step_execution_id")
  def eventType           = column[String]("event_type")
  def eventPayload        = column[Json]("event_payload")
  def occurredAt          = column[Instant]("occurred_at")
  def receivedAt          = column[Instant]("received_at")
  def correlationId       = column[UUID]("correlation_id")

  def * = (
    eventId.?,
    eventUUID.?,
    executionId,
    workflowExecutionId,
    jobExecutionId,
    stepExecutionId,
    eventType,
    eventPayload,
    occurredAt,
    receivedAt,
    correlationId
  ) <> (ExecutionEventRow.apply.tupled, ExecutionEventRow.unapply)

  def pk = primaryKey("event_id_occurred_at_pk", (eventId, occurredAt))

  def fk_executionId =
    foreignKey("execution_id_fk", executionId, TableQuery[ExecutionTable])(_.executionId, onDelete = Cascade)
