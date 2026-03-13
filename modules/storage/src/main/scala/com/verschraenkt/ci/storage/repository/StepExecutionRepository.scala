package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.executionStatusMapper
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.{ StepExecutionRow, StepExecutionTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant

/** Repository for step execution persistence operations */
sealed trait IStepExecutionRepository:
  /** Create a new step execution */
  def create(exec: StepExecutionRow): IO[Unit]

  /** Find a step execution by ID */
  def findById(id: Long): IO[Option[StepExecutionRow]]

  /** Find all step executions for a job execution, ordered by step index */
  def findByJobExecutionId(jobExecutionId: Long): IO[Seq[StepExecutionRow]]

  /** Update step execution status */
  def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String] = None): IO[Boolean]

  /** Update step execution start time */
  def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean]

  /** Update step execution completion */
  def updateCompleted(id: Long, completedAt: Instant, exitCode: Option[Int]): IO[Boolean]

  /** Update step output locations */
  def updateOutputLocations(
      id: Long,
      stdoutLocation: Option[String],
      stderrLocation: Option[String]
  ): IO[Boolean]

/** Slick-based implementation */
class StepExecutionRepository(val dbModule: DatabaseModule)
    extends IStepExecutionRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[StepExecutionRow]:
  import PostgresProfile.api.*

  override def create(exec: StepExecutionRow): IO[Unit] =
    withContext("create") {
      StepExecutionInsert.insert(exec)
    }

  override def findById(id: Long): IO[Option[StepExecutionRow]] =
    withContext("findById") {
      val query = table
        .filter(_.stepExecutionId === id)
        .result
        .headOption

      run(query)
    }

  override def findByJobExecutionId(jobExecutionId: Long): IO[Seq[StepExecutionRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.stepIndex.asc)
        .result

      run(query)
    }

  override def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String]): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.stepExecutionId === id)
        .map(e => (e.status, e.errorMessage))
        .update((status, errorMessage))

      run(q).map(_ == 1)
    }

  override def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean] =
    withContext("updateStartedAt") {
      val q = table
        .filter(_.stepExecutionId === id)
        .map(_.startedAt)
        .update(Some(startedAt))

      run(q).map(_ == 1)
    }

  override def updateCompleted(id: Long, completedAt: Instant, exitCode: Option[Int]): IO[Boolean] =
    withContext("updateCompleted") {
      val q = table
        .filter(_.stepExecutionId === id)
        .map(e => (e.completedAt, e.exitCode))
        .update((Some(completedAt), exitCode))

      run(q).map(_ == 1)
    }

  override def updateOutputLocations(
      id: Long,
      stdoutLocation: Option[String],
      stderrLocation: Option[String]
  ): IO[Boolean] =
    withContext("updateOutputLocations") {
      val q = table
        .filter(_.stepExecutionId === id)
        .map(e => (e.stdoutLocation, e.stderrLocation))
        .update((stdoutLocation, stderrLocation))

      run(q).map(_ == 1)
    }

  override def table: TableQuery[StepExecutionTable] = TableQuery[StepExecutionTable]

  private object StepExecutionInsert extends InsertActionRaw:

    protected def entityName = "StepExecutionDB"

    protected def getId(entry: StepExecutionRow): String = entry.stepExecutionId.toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      StepExecutionRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      StepExecutionRepository.this.fail(error)
