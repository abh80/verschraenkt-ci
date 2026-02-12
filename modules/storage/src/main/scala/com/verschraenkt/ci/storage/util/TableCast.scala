package com.verschraenkt.ci.storage.util


import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.errors.StorageError
import org.postgresql.util.PSQLException


trait TableCast[R]:
  protected def table: TableQuery[? <: Table[R]]

  trait InsertAction[D, M]:
    protected def mapper: DomainRowMapper[D, R, M]
    protected def entityName: String
    protected def getId(domain: D): String

    protected def transactionally[T](action: DBIO[T]): IO[T]
    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T]
    protected def isDuplicateKeyError(e: PSQLException): Boolean =
      e.getSQLState == "23505"

    def insert(domain: D, metadata: M)(using enc: io.circe.Encoder[D])(using applicationContext: ApplicationContext): IO[D] =
      val row = mapper.fromDomain(domain, metadata)
      val insertAction = table += row

      transactionally(insertAction)
        .map(_ => domain)
        .handleErrorWith {
          case e: PSQLException if isDuplicateKeyError(e) =>
            fail(StorageError.DuplicateKey(entityName, getId(domain)))
          case e: java.sql.SQLException =>
            fail(StorageError.ConnectionFailed(e))
          case e: Exception =>
            fail(StorageError.TransactionFailed(e))
        }