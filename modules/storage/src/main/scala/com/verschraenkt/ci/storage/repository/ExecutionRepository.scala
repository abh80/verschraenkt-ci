/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.executionStatusMapper
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.db.tables.{ ExecutionRow, ExecutionTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

/** Repository for execution persistence operations */
trait IExecutionRepository:
  /** Find an execution by ID (only non-deleted) */
  def findById(id: UUID): IO[Option[ExecutionRow]]

  /** Find execution by ID including deleted ones */
  def findByIdIncludingDeleted(id: UUID): IO[Option[ExecutionRow]]

  /** Find all executions for a pipeline with optional status filter */
  def findByPipelineId(
      pipelineId: PipelineId,
      status: Option[ExecutionStatus] = None,
      limit: Int = 100
  ): IO[Seq[ExecutionRow]]

  /** Find all queued executions */
  def findQueued(limit: Int = 100): IO[Seq[ExecutionRow]]

  /** Find all running executions */
  def findRunning(limit: Int = 100): IO[Seq[ExecutionRow]]

  /** Find executions by concurrency group */
  def findByConcurrencyGroup(group: String, limit: Int = 100): IO[Seq[ExecutionRow]]

  /** Find executions by idempotency key */
  def findByIdempotencyKey(key: String): IO[Option[ExecutionRow]]

  /** Create a new execution */
  def create(execution: ExecutionRow): IO[UUID]

  /** Update execution status */
  def updateStatus(id: UUID, status: ExecutionStatus, errorMessage: Option[String] = None): IO[Boolean]

  /** Update execution start time */
  def updateStartedAt(id: UUID, startedAt: Instant): IO[Boolean]

  /** Update execution completion time */
  def updateCompletedAt(id: UUID, completedAt: Instant): IO[Boolean]

  /** Update execution resource usage */
  def updateResourceUsage(id: UUID, cpuMilliSeconds: Long, memoryMibSeconds: Long): IO[Boolean]

  /** Update concurrency queue position */
  def updateConcurrencyQueuePosition(id: UUID, position: Int): IO[Boolean]

  /** Soft-delete an execution */
  def softDelete(id: UUID, deletedBy: User): IO[Boolean]

  /** Find executions that have timed out */
  def findTimedOut(at: Instant = Instant.now(), limit: Int = 100): IO[Seq[ExecutionRow]]

/** Slick-based implementation */
class ExecutionRepository(
    protected val dbModule: DatabaseModule
) extends IExecutionRepository
    with StorageContext
    with DatabaseOperations
    with TableCast[ExecutionRow]:

  import profile.api.*

  /** Find an execution by ID (only non-deleted) */
  override def findById(id: UUID): IO[Option[ExecutionRow]] =
    withContext("findById") {
      val query = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      run(query)
    }

  /** Find execution by ID including deleted ones */
  override def findByIdIncludingDeleted(id: UUID): IO[Option[ExecutionRow]] =
    withContext("findByIdIncludingDeleted") {
      val query = table
        .filter(_.executionId === id)
        .result
        .headOption

      run(query)
    }

  /** Find all executions for a pipeline with optional status filter */
  override def findByPipelineId(
      pipelineId: PipelineId,
      status: Option[ExecutionStatus],
      limit: Int
  ): IO[Seq[ExecutionRow]] =
    withContext("findByPipelineId") {
      // Base query: only non-deleted executions for this pipeline
      val baseQuery = table
        .filter(_.pipelineId === pipelineId)
        .filter(_.deletedAt.isEmpty)

      // Apply status filtering if requested
      val statusFilteredQuery = status match
        case Some(s) => baseQuery.filter(_.status === s)
        case None    => baseQuery

      // Sort by queued_at descending and apply limit
      val query = statusFilteredQuery
        .sortBy(_.queuedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  /** Find all queued executions */
  override def findQueued(limit: Int): IO[Seq[ExecutionRow]] =
    withContext("findQueued") {
      val query = table
        .filter(_.status === ExecutionStatus.Pending)
        .filter(_.deletedAt.isEmpty)
        .sortBy(_.queuedAt.asc)
        .take(limit)
        .result

      run(query)
    }

  /** Find all running executions */
  override def findRunning(limit: Int): IO[Seq[ExecutionRow]] =
    withContext("findRunning") {
      val query = table
        .filter(_.status === ExecutionStatus.Running)
        .filter(_.deletedAt.isEmpty)
        .sortBy(_.startedAt.desc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  /** Find executions by concurrency group */
  override def findByConcurrencyGroup(group: String, limit: Int): IO[Seq[ExecutionRow]] =
    withContext("findByConcurrencyGroup") {
      val query = table
        .filter(_.concurrencyGroup === group)
        .filter(_.deletedAt.isEmpty)
        .sortBy(_.concurrencyQueuePosition.asc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  /** Find executions by idempotency key */
  override def findByIdempotencyKey(key: String): IO[Option[ExecutionRow]] =
    withContext("findByIdempotencyKey") {
      val query = table
        .filter(_.idempotencyKey === key)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      run(query)
    }

  /** Create a new execution */
  override def create(execution: ExecutionRow): IO[UUID] =
    withContext("create") {
      val insertAction = table += execution

      transactionally(insertAction)
        .map(_ => execution.executionId)
        .handleErrorWith {
          case e: org.postgresql.util.PSQLException if isDuplicateKeyError(e) =>
            fail(StorageError.DuplicateKey("Execution", execution.executionId.toString))
          case e: java.sql.SQLException =>
            fail(StorageError.ConnectionFailed(e))
          case e: Exception =>
            fail(StorageError.TransactionFailed(e))
        }
    }

  /** Update execution status */
  override def updateStatus(id: UUID, status: ExecutionStatus, errorMessage: Option[String]): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(e => (e.status, e.errorMessage))
        .update((status, errorMessage))

      run(q).map(_ == 1)
    }

  /** Update execution start time */
  override def updateStartedAt(id: UUID, startedAt: Instant): IO[Boolean] =
    withContext("updateStartedAt") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(_.startedAt)
        .update(Some(startedAt))

      run(q).map(_ == 1)
    }

  /** Update execution completion time */
  override def updateCompletedAt(id: UUID, completedAt: Instant): IO[Boolean] =
    withContext("updateCompletedAt") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(_.completedAt)
        .update(Some(completedAt))

      run(q).map(_ == 1)
    }

  /** Update execution resource usage */
  override def updateResourceUsage(id: UUID, cpuMilliSeconds: Long, memoryMibSeconds: Long): IO[Boolean] =
    withContext("updateResourceUsage") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(e => (e.totalCpuMilliSeconds, e.totalMemoryMibSeconds))
        .update((cpuMilliSeconds, memoryMibSeconds))

      run(q).map(_ == 1)
    }

  /** Update concurrency queue position */
  override def updateConcurrencyQueuePosition(id: UUID, position: Int): IO[Boolean] =
    withContext("updateConcurrencyQueuePosition") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(_.concurrencyQueuePosition)
        .update(Some(position))

      run(q).map(_ == 1)
    }

  /** Soft-delete an execution */
  override def softDelete(id: UUID, deletedBy: User): IO[Boolean] =
    withContext("softDelete") {
      val q = table
        .filter(_.executionId === id)
        .filter(_.deletedAt.isEmpty)
        .map(_.deletedAt)
        .update(Some(Instant.now()))

      run(q).map(_ == 1)
    }

  /** Find executions that have timed out */
  override def findTimedOut(at: Instant, limit: Int): IO[Seq[ExecutionRow]] =
    withContext("findTimedOut") {
      val query = table
        .filter(_.timeoutAt.isDefined)
        .filter(_.timeoutAt < at)
        .filter(e => e.status === ExecutionStatus.Running || e.status === ExecutionStatus.Pending)
        .filter(_.deletedAt.isEmpty)
        .sortBy(_.timeoutAt.asc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  override protected def table: TableQuery[ExecutionTable] = TableQuery[ExecutionTable]

  private def isDuplicateKeyError(e: org.postgresql.util.PSQLException): Boolean =
    e.getSQLState == "23505"
