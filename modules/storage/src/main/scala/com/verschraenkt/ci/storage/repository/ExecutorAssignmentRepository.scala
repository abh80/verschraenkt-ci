package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ ExecutorAssignmentRow, ExecutorAssignmentTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

sealed trait IExecutorAssignmentRepository:
  /** Create a new executor assignment */
  def create(assignment: ExecutorAssignmentRow): IO[UUID]

  /** Find an assignment by its ID */
  def findById(assignmentId: UUID): IO[Option[ExecutorAssignmentRow]]

  /** Find an assignment by job execution ID */
  def findByJobExecutionId(jobExecutionId: Long): IO[Option[ExecutorAssignmentRow]]

  /** Find all assignments for an executor */
  def findByExecutorId(executorId: UUID, limit: Int = 100): IO[Seq[ExecutorAssignmentRow]]

  /** Update the started_at timestamp */
  def updateStartedAt(assignmentId: UUID, startedAt: Instant): IO[Boolean]

  /** Update the completed_at timestamp */
  def updateCompletedAt(assignmentId: UUID, completedAt: Instant): IO[Boolean]

class ExecutorAssignmentRepository(val dbModule: DatabaseModule)
    extends IExecutorAssignmentRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[ExecutorAssignmentRow]:
  import PostgresProfile.api.*

  override def create(assignment: ExecutorAssignmentRow): IO[UUID] =
    withContext("create") {
      val applicationContext = summon[ApplicationContext]

      val insertAction = table.returning(table.map(_.assignmentId)) += assignment
      ExecutorAssignmentInsert.runTransactionWithDefaultFailureHandle(
        transactionally(insertAction)
      )(using applicationContext, assignment)
    }

  override def findById(assignmentId: UUID): IO[Option[ExecutorAssignmentRow]] =
    withContext("findById") {
      val query = table
        .filter(_.assignmentId === assignmentId)
        .result
        .headOption

      run(query)
    }

  override def findByJobExecutionId(jobExecutionId: Long): IO[Option[ExecutorAssignmentRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .result
        .headOption

      run(query)
    }

  override def findByExecutorId(executorId: UUID, limit: Int): IO[Seq[ExecutorAssignmentRow]] =
    withContext("findByExecutorId") {
      val query = table
        .filter(_.executorId === executorId)
        .sortBy(_.assignedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def updateStartedAt(assignmentId: UUID, startedAt: Instant): IO[Boolean] =
    withContext("updateStartedAt") {
      val q = table
        .filter(_.assignmentId === assignmentId)
        .map(_.startedAt)
        .update(Some(startedAt))

      run(q).map(_ == 1)
    }

  override def updateCompletedAt(assignmentId: UUID, completedAt: Instant): IO[Boolean] =
    withContext("updateCompletedAt") {
      val q = table
        .filter(_.assignmentId === assignmentId)
        .map(_.completedAt)
        .update(Some(completedAt))

      run(q).map(_ == 1)
    }

  override def table: TableQuery[ExecutorAssignmentTable] = TableQuery[ExecutorAssignmentTable]

  private object ExecutorAssignmentInsert extends InsertActionRaw:

    protected def entityName = "ExecutorAssignmentDB"

    protected def getId(entry: ExecutorAssignmentRow): String =
      entry.assignmentId.getOrElse("none").toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      ExecutorAssignmentRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      ExecutorAssignmentRepository.this.fail(error)
