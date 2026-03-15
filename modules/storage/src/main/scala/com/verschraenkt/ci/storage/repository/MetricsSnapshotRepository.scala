package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ MetricsSnapshotRow, MetricsSnapshotTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant

sealed trait IMetricsSnapshotRepository:
  /** Create a new metrics snapshot */
  def create(snapshot: MetricsSnapshotRow): IO[Unit]

  /** Find snapshots by job execution ID */
  def findByJobExecutionId(jobExecutionId: Long, limit: Int = 1000): IO[Seq[MetricsSnapshotRow]]

  /** Find snapshots by job execution ID within a time range */
  def findByJobExecutionIdInRange(
      jobExecutionId: Long,
      from: Instant,
      to: Instant,
      limit: Int = 1000
  ): IO[Seq[MetricsSnapshotRow]]

  /** Find the latest snapshot for a job execution */
  def findLatest(jobExecutionId: Long): IO[Option[MetricsSnapshotRow]]

class MetricsSnapshotRepository(val dbModule: DatabaseModule)
    extends IMetricsSnapshotRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[MetricsSnapshotRow]:
  import PostgresProfile.api.*

  override def table: TableQuery[MetricsSnapshotTable] = TableQuery[MetricsSnapshotTable]

  override def create(snapshot: MetricsSnapshotRow): IO[Unit] =
    withContext("create") {
      MetricsSnapshotInsert.insert(snapshot)
    }

  override def findByJobExecutionId(jobExecutionId: Long, limit: Int): IO[Seq[MetricsSnapshotRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.timestamp.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByJobExecutionIdInRange(
      jobExecutionId: Long,
      from: Instant,
      to: Instant,
      limit: Int
  ): IO[Seq[MetricsSnapshotRow]] =
    withContext("findByJobExecutionIdInRange") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .filter(_.timestamp >= from)
        .filter(_.timestamp <= to)
        .sortBy(_.timestamp.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findLatest(jobExecutionId: Long): IO[Option[MetricsSnapshotRow]] =
    withContext("findLatest") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.timestamp.desc)
        .take(1)
        .result
        .headOption

      run(query)
    }

  private object MetricsSnapshotInsert extends InsertActionRaw:

    protected def entityName = "MetricsSnapshotDB"

    protected def getId(entry: MetricsSnapshotRow): String = entry.snapshotId.getOrElse(0L).toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      MetricsSnapshotRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      MetricsSnapshotRepository.this.fail(error)
