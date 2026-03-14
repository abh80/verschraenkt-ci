package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ ExecutionEventRow, ExecutionEventTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.util.UUID

sealed trait IExecutionEventRepository:
  /** Create a new execution event */
  def create(event: ExecutionEventRow): IO[Unit]

  /** Find events by parent execution ID */
  def findByExecutionId(executionId: Long, limit: Int = 1000): IO[Seq[ExecutionEventRow]]

  /** Find events by workflow execution ID */
  def findByWorkflowExecutionId(workflowExecutionId: Long, limit: Int = 1000): IO[Seq[ExecutionEventRow]]

  /** Find events by job execution ID */
  def findByJobExecutionId(jobExecutionId: Long, limit: Int = 1000): IO[Seq[ExecutionEventRow]]

  /** Find events by step execution ID */
  def findByStepExecutionId(stepExecutionId: Long, limit: Int = 1000): IO[Seq[ExecutionEventRow]]

  /** Find events by correlation ID */
  def findByCorrelationId(correlationId: UUID): IO[Seq[ExecutionEventRow]]

  /** Find events by execution ID and event type */
  def findByEventType(executionId: Long, eventType: String, limit: Int = 1000): IO[Seq[ExecutionEventRow]]

class ExecutionEventRepository(val dbModule: DatabaseModule)
    extends IExecutionEventRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[ExecutionEventRow]:
  import PostgresProfile.api.*

  override def create(event: ExecutionEventRow): IO[Unit] =
    withContext("create") {
      ExecutionEventInsert.insert(event)
    }

  override def findByExecutionId(executionId: Long, limit: Int): IO[Seq[ExecutionEventRow]] =
    withContext("findByExecutionId") {
      val query = table
        .filter(_.executionId === executionId)
        .sortBy(_.occurredAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByWorkflowExecutionId(workflowExecutionId: Long, limit: Int): IO[Seq[ExecutionEventRow]] =
    withContext("findByWorkflowExecutionId") {
      val query = table
        .filter(_.workflowExecutionId === workflowExecutionId)
        .sortBy(_.occurredAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByJobExecutionId(jobExecutionId: Long, limit: Int): IO[Seq[ExecutionEventRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.occurredAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByStepExecutionId(stepExecutionId: Long, limit: Int): IO[Seq[ExecutionEventRow]] =
    withContext("findByStepExecutionId") {
      val query = table
        .filter(_.stepExecutionId === stepExecutionId)
        .sortBy(_.occurredAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByCorrelationId(correlationId: UUID): IO[Seq[ExecutionEventRow]] =
    withContext("findByCorrelationId") {
      val query = table
        .filter(_.correlationId === correlationId)
        .sortBy(_.occurredAt.desc)
        .result

      run(query)
    }

  override def findByEventType(executionId: Long, eventType: String, limit: Int): IO[Seq[ExecutionEventRow]] =
    withContext("findByEventType") {
      val query = table
        .filter(_.executionId === executionId)
        .filter(_.eventType === eventType)
        .sortBy(_.occurredAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def table: TableQuery[ExecutionEventTable] = TableQuery[ExecutionEventTable]

  private object ExecutionEventInsert extends InsertActionRaw:

    protected def entityName = "ExecutionEventDB"

    protected def getId(entry: ExecutionEventRow): String = entry.executionId.toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      ExecutionEventRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      ExecutionEventRepository.this.fail(error)
