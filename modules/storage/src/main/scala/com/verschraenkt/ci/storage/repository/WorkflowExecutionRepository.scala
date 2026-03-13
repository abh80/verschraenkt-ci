package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.executionStatusMapper
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.{ WorkflowExecutionRow, WorkflowExecutionTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant

sealed trait IWorkflowExecutionRepository:
  /** Create a new workflow execution */
  def create(exec: WorkflowExecutionRow): IO[Unit]

  /** Find a workflow execution by ID */
  def findById(id: Long): IO[Option[WorkflowExecutionRow]]

  /** Find all workflow executions for a pipeline execution */
  def findByExecutionId(executionId: Long): IO[Seq[WorkflowExecutionRow]]

  /** Find workflow executions by execution ID and status */
  def findByExecutionIdAndStatus(executionId: Long, status: ExecutionStatus): IO[Seq[WorkflowExecutionRow]]

  /** Update workflow execution status */
  def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String] = None): IO[Boolean]

  /** Update workflow execution start time */
  def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean]

  /** Update workflow execution completion time */
  def updateCompletedAt(id: Long, completedAt: Instant): IO[Boolean]

  /** Find workflow executions that have timed out */
  def findTimedOut(at: Instant = Instant.now(), limit: Int = 100): IO[Seq[WorkflowExecutionRow]]

class WorkflowExecutionRepository(val dbModule: DatabaseModule)
    extends IWorkflowExecutionRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[WorkflowExecutionRow]:
  import PostgresProfile.api.*

  override def create(exec: WorkflowExecutionRow): IO[Unit] =
    withContext("create") {
      WorkflowExecutionInsert.insert(exec)
    }

  override def findById(id: Long): IO[Option[WorkflowExecutionRow]] =
    withContext("findById") {
      val query = table
        .filter(_.workflowExecutionId === id)
        .result
        .headOption

      run(query)
    }

  override def findByExecutionId(executionId: Long): IO[Seq[WorkflowExecutionRow]] =
    withContext("findByExecutionId") {
      val query = table
        .filter(_.executionId === executionId)
        .sortBy(_.queuedAt.desc)
        .result

      run(query)
    }

  override def findByExecutionIdAndStatus(
      executionId: Long,
      status: ExecutionStatus
  ): IO[Seq[WorkflowExecutionRow]] =
    withContext("findByExecutionIdAndStatus") {
      val query = table
        .filter(_.executionId === executionId)
        .filter(_.status === status)
        .sortBy(_.queuedAt.desc)
        .result

      run(query)
    }

  override def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String]): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.workflowExecutionId === id)
        .map(e => (e.status, e.errorMessage))
        .update((status, errorMessage))

      run(q).map(_ == 1)
    }

  override def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean] =
    withContext("updateStartedAt") {
      val q = table
        .filter(_.workflowExecutionId === id)
        .map(_.startedAt)
        .update(Some(startedAt))

      run(q).map(_ == 1)
    }

  override def updateCompletedAt(id: Long, completedAt: Instant): IO[Boolean] =
    withContext("updateCompletedAt") {
      val q = table
        .filter(_.workflowExecutionId === id)
        .map(_.completedAt)
        .update(Some(completedAt))

      run(q).map(_ == 1)
    }

  // noinspection DuplicatedCode
  override def findTimedOut(at: Instant, limit: Int): IO[Seq[WorkflowExecutionRow]] =
    withContext("findTimedOut") {
      val query = table
        .filter(_.timeoutAt.isDefined)
        .filter(_.timeoutAt < at)
        .filter(e => e.status === ExecutionStatus.Running || e.status === ExecutionStatus.Pending)
        .sortBy(_.timeoutAt.asc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  override def table: TableQuery[WorkflowExecutionTable] = TableQuery[WorkflowExecutionTable]

  private object WorkflowExecutionInsert extends InsertActionRaw:

    protected def entityName = "WorkflowExecutionDB"

    protected def getId(entry: WorkflowExecutionRow): String = entry.executionId.toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      WorkflowExecutionRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      WorkflowExecutionRepository.this.fail(error)
