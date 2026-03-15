package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ SecretAccessLogRow, SecretAccessLogTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.util.UUID

sealed trait ISecretAccessLogRepository:
  /** Log a secret access attempt */
  def create(entry: SecretAccessLogRow): IO[Long]

  /** Find access logs by secret ID */
  def findBySecretId(secretId: UUID, limit: Int = 100): IO[Seq[SecretAccessLogRow]]

  /** Find access logs by job execution ID */
  def findByJobExecutionId(jobExecutionId: Long): IO[Seq[SecretAccessLogRow]]

  /** Find denied access attempts */
  def findDenied(limit: Int = 100): IO[Seq[SecretAccessLogRow]]

class SecretAccessLogRepository(val dbModule: DatabaseModule)
    extends ISecretAccessLogRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[SecretAccessLogRow]:
  import PostgresProfile.api.*

  override def table: TableQuery[SecretAccessLogTable] = TableQuery[SecretAccessLogTable]

  override def create(entry: SecretAccessLogRow): IO[Long] =
    withContext("create") {
      val applicationContext = summon[ApplicationContext]

      val insertAction = table.returning(table.map(_.logId)) += entry
      AccessLogInsert.runTransactionWithDefaultFailureHandle(
        transactionally(insertAction)
      )(using applicationContext, entry)
    }

  override def findBySecretId(secretId: UUID, limit: Int): IO[Seq[SecretAccessLogRow]] =
    withContext("findBySecretId") {
      val query = table
        .filter(_.secretId === secretId)
        .sortBy(_.accessedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findByJobExecutionId(jobExecutionId: Long): IO[Seq[SecretAccessLogRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.accessedAt.desc)
        .result

      run(query)
    }

  override def findDenied(limit: Int): IO[Seq[SecretAccessLogRow]] =
    withContext("findDenied") {
      val query = table
        .filter(_.granted === false)
        .sortBy(_.accessedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  private object AccessLogInsert extends InsertActionRaw:

    protected def entityName = "SecretAccessLogDB"

    protected def getId(entry: SecretAccessLogRow): String = entry.logId.getOrElse(0L).toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      SecretAccessLogRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      SecretAccessLogRepository.this.fail(error)
