package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.engine.api.ExecutorId
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{
  executorStatusMapper,
  simpleArrayColumnExtensionMethods,
  simpleStrListTypeMapper
}
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutorStatus
import com.verschraenkt.ci.storage.db.tables.{ ExecutorRow, ExecutorTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

sealed trait IExecutorRepository:
  def findById(id: ExecutorId): IO[Option[ExecutorRow]]
  def findByStatus(status: ExecutorStatus, limit: Int = 100): IO[Vector[ExecutorRow]]
  def findActive(labels: Option[Set[String]] = None, limit: Int = 100): IO[Vector[ExecutorRow]]
  def save(row: ExecutorRow): IO[UUID]
  def updateStatus(id: ExecutorId, status: ExecutorStatus): IO[Boolean]
  def updateHeartbeat(id: ExecutorId, heartbeat: Instant): IO[Boolean]
  def softDelete(id: ExecutorId): IO[Boolean]

class ExecutorRepository(
    protected val dbModule: DatabaseModule
) extends IExecutorRepository
    with StorageContext
    with DatabaseOperations
    with TableCast[ExecutorRow]:
  import PostgresProfile.api.*

  override protected def table: TableQuery[ExecutorTable] = TableQuery[ExecutorTable]

  override def findById(id: ExecutorId): IO[Option[ExecutorRow]] =
    withContext("findById") {
      val query = table
        .filter(_.executorId === id.value)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      run(query)
    }

  override def findByStatus(status: ExecutorStatus, limit: Int): IO[Vector[ExecutorRow]] =
    withContext("findByStatus") {
      val query = table
        .filter(_.status === status)
        .filter(_.deletedAt.isEmpty)
        .sortBy(_.lastHeartbeat.desc)
        .take(limit)
        .result

      run(query).map(_.toVector)
    }

  override def findActive(labels: Option[Set[String]], limit: Int): IO[Vector[ExecutorRow]] =
    withContext("findActive") {
      val baseQuery = table
        .filter(_.deletedAt.isEmpty)

      val labelFilteredQuery: Query[ExecutorTable, ExecutorRow, Seq] = labels match
        case Some(requestedLabels) if requestedLabels.nonEmpty =>
          baseQuery.filter(row => row.labels @> requestedLabels.toList)
        case _ => baseQuery

      val query = labelFilteredQuery
        .sortBy(_.lastHeartbeat.desc)
        .take(limit)
        .result

      run(query).map(_.toVector)
    }

  override def save(row: ExecutorRow): IO[UUID] =
    withContext("save") {
      val applicationContext = summon[ApplicationContext]

      val insertAction = table.returning(table.map(_.executorId)) += row
      ExecutorInsert.runTransactionWithDefaultFailureHandle(
        transactionally(insertAction)
      )(using applicationContext, row)
    }

  override def updateStatus(id: ExecutorId, status: ExecutorStatus): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.executorId === id.value)
        .filter(_.deletedAt.isEmpty)
        .map(_.status)
        .update(status)

      run(q).map(_ == 1)
    }

  override def updateHeartbeat(id: ExecutorId, heartbeat: Instant): IO[Boolean] =
    withContext("updateHeartbeat") {
      val q = table
        .filter(_.executorId === id.value)
        .filter(_.deletedAt.isEmpty)
        .map(_.lastHeartbeat)
        .update(heartbeat)

      run(q).map(_ == 1)
    }

  override def softDelete(id: ExecutorId): IO[Boolean] =
    withContext("softDelete") {
      val q = table
        .filter(_.executorId === id.value)
        .filter(_.deletedAt.isEmpty)
        .map(_.deletedAt)
        .update(Some(Instant.now()))

      run(q).map(_ == 1)
    }

  private object ExecutorInsert extends InsertActionRaw:

    protected def entityName = "ExecutorDB"

    protected def getId(entry: ExecutorRow): String = entry.executorId.getOrElse("none").toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      ExecutorRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      ExecutorRepository.this.fail(error)
