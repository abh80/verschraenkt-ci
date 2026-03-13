package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.executionStatusMapper
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.{ JobExecutionRow, JobExecutionTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

/** Repository for job execution persistence operations */
sealed trait IJobExecutionRepository:
  /** Create a new job execution */
  def create(exec: JobExecutionRow): IO[Unit]

  /** Find a job execution by ID */
  def findById(id: Long): IO[Option[JobExecutionRow]]

  /** Find all job executions for a workflow execution */
  def findByWorkflowExecutionId(workflowExecutionId: Long): IO[Seq[JobExecutionRow]]

  /** Find all job executions for a pipeline execution */
  def findByExecutionId(executionId: Long): IO[Seq[JobExecutionRow]]

  /** Find job executions by status */
  def findByStatus(status: ExecutionStatus, limit: Int = 100): IO[Seq[JobExecutionRow]]

  /** Update job execution status */
  def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String] = None): IO[Boolean]

  /** Assign an executor to a job execution */
  def assignExecutor(id: Long, executorId: UUID, assignedAt: Instant): IO[Boolean]

  /** Update job execution start time */
  def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean]

  /** Update job execution completion time and exit code */
  def updateCompleted(id: Long, completedAt: Instant, exitCode: Option[Int]): IO[Boolean]

  /** Update actual resource usage */
  def updateResourceUsage(id: Long, cpuMilliSeconds: Long, memoryMibSeconds: Long): IO[Boolean]

  /** Find job executions that have timed out */
  def findTimedOut(at: Instant = Instant.now(), limit: Int = 100): IO[Seq[JobExecutionRow]]

/** Slick-based implementation */
class JobExecutionRepository(val dbModule: DatabaseModule)
    extends IJobExecutionRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[JobExecutionRow]:
  import PostgresProfile.api.*

  override def create(exec: JobExecutionRow): IO[Unit] =
    withContext("create") {
      JobExecutionInsert.insert(exec)
    }

  override def findById(id: Long): IO[Option[JobExecutionRow]] =
    withContext("findById") {
      val query = table
        .filter(_.jobExecutionId === id)
        .result
        .headOption

      run(query)
    }

  override def findByWorkflowExecutionId(workflowExecutionId: Long): IO[Seq[JobExecutionRow]] =
    withContext("findByWorkflowExecutionId") {
      val query = table
        .filter(_.workflowExecutionId === workflowExecutionId)
        .result

      run(query)
    }

  override def findByExecutionId(executionId: Long): IO[Seq[JobExecutionRow]] =
    withContext("findByExecutionId") {
      val query = table
        .filter(_.executionId === executionId)
        .result

      run(query)
    }

  override def findByStatus(status: ExecutionStatus, limit: Int): IO[Seq[JobExecutionRow]] =
    withContext("findByStatus") {
      val query = table
        .filter(_.status === status)
        .take(limit)
        .result

      run(query)
    }

  override def updateStatus(id: Long, status: ExecutionStatus, errorMessage: Option[String]): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.jobExecutionId === id)
        .map(e => (e.status, e.errorMessage))
        .update((status, errorMessage))

      run(q).map(_ == 1)
    }

  override def assignExecutor(id: Long, executorId: UUID, assignedAt: Instant): IO[Boolean] =
    withContext("assignExecutor") {
      val q = table
        .filter(_.jobExecutionId === id)
        .map(e => (e.executorId, e.assignedAt))
        .update((Some(executorId), Some(assignedAt)))

      run(q).map(_ == 1)
    }

  override def updateStartedAt(id: Long, startedAt: Instant): IO[Boolean] =
    withContext("updateStartedAt") {
      val q = table
        .filter(_.jobExecutionId === id)
        .map(_.startedAt)
        .update(Some(startedAt))

      run(q).map(_ == 1)
    }

  override def updateCompleted(id: Long, completedAt: Instant, exitCode: Option[Int]): IO[Boolean] =
    withContext("updateCompleted") {
      val q = table
        .filter(_.jobExecutionId === id)
        .map(e => (e.completedAt, e.exitCode))
        .update((Some(completedAt), exitCode))

      run(q).map(_ == 1)
    }

  override def updateResourceUsage(id: Long, cpuMilliSeconds: Long, memoryMibSeconds: Long): IO[Boolean] =
    withContext("updateResourceUsage") {
      val q = table
        .filter(_.jobExecutionId === id)
        .map(e => (e.actualCpuMilliSeconds, e.actualMemoryMibSeconds))
        .update((Some(cpuMilliSeconds), Some(memoryMibSeconds)))

      run(q).map(_ == 1)
    }

  // noinspection DuplicatedCode
  override def findTimedOut(at: Instant, limit: Int): IO[Seq[JobExecutionRow]] =
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

  override def table: TableQuery[JobExecutionTable] = TableQuery[JobExecutionTable]

  private object JobExecutionInsert extends InsertActionRaw:

    protected def entityName = "JobExecutionDB"

    protected def getId(entry: JobExecutionRow): String = entry.jobExecutionId.toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      JobExecutionRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      JobExecutionRepository.this.fail(error)
