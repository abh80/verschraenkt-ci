package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.secretScopeMapper
import com.verschraenkt.ci.storage.db.codecs.Enums.SecretScope
import com.verschraenkt.ci.storage.db.tables.{ SecretRow, SecretTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

sealed trait ISecretRepository:
  /** Create a new secret */
  def create(secret: SecretRow): IO[UUID]

  /** Find a secret by its ID */
  def findById(secretId: UUID): IO[Option[SecretRow]]

  /** Find a secret by name and scope */
  def findByNameAndScope(
      name: String,
      scope: SecretScope,
      scopeEntityId: Option[String]
  ): IO[Option[SecretRow]]

  /** Find all active secrets for a scope */
  def findByScope(scope: SecretScope, scopeEntityId: Option[String]): IO[Seq[SecretRow]]

  /** Update vault path (re-encryption / rotation) */
  def updateVaultPath(secretId: UUID, vaultPathEncrypted: Array[Byte], vaultPathKeyId: String): IO[Boolean]

  /** Rotate a secret (bump version, update rotatedAt) */
  def rotate(secretId: UUID, newVaultPathEncrypted: Array[Byte], newVaultPathKeyId: String): IO[Boolean]

  /** Deactivate a secret */
  def deactivate(secretId: UUID): IO[Boolean]

  /** Soft-delete a secret */
  def softDelete(secretId: UUID, deletedBy: String): IO[Boolean]

  /** Find secrets due for rotation */
  def findDueForRotation(before: Instant, limit: Int = 100): IO[Seq[SecretRow]]

class SecretRepository(val dbModule: DatabaseModule)
    extends ISecretRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[SecretRow]:
  import PostgresProfile.api.*

  override def table: TableQuery[SecretTable] = TableQuery[SecretTable]

  override def create(secret: SecretRow): IO[UUID] =
    withContext("create") {
      val applicationContext = summon[ApplicationContext]

      val insertAction = table.returning(table.map(_.secretId)) += secret
      SecretInsert.runTransactionWithDefaultFailureHandle(
        transactionally(insertAction)
      )(using applicationContext, secret)
    }

  override def findById(secretId: UUID): IO[Option[SecretRow]] =
    withContext("findById") {
      val query = table
        .filter(_.secretId === secretId)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      run(query)
    }

  override def findByNameAndScope(
      name: String,
      scope: SecretScope,
      scopeEntityId: Option[String]
  ): IO[Option[SecretRow]] =
    withContext("findByNameAndScope") {
      val baseQuery = table
        .filter(_.name === name)
        .filter(_.scope === scope)
        .filter(_.deletedAt.isEmpty)
        .filter(_.isActive === true)

      val query = scopeEntityId match
        case Some(entityId) => baseQuery.filter(_.scopeEntityId === entityId)
        case None           => baseQuery.filter(_.scopeEntityId.isEmpty)

      run(query.result.headOption)
    }

  override def findByScope(scope: SecretScope, scopeEntityId: Option[String]): IO[Seq[SecretRow]] =
    withContext("findByScope") {
      val baseQuery = table
        .filter(_.scope === scope)
        .filter(_.deletedAt.isEmpty)
        .filter(_.isActive === true)

      val query = scopeEntityId match
        case Some(entityId) => baseQuery.filter(_.scopeEntityId === entityId)
        case None           => baseQuery.filter(_.scopeEntityId.isEmpty)

      run(query.sortBy(_.name.asc).result)
    }

  override def updateVaultPath(
      secretId: UUID,
      vaultPathEncrypted: Array[Byte],
      vaultPathKeyId: String
  ): IO[Boolean] =
    withContext("updateVaultPath") {
      val q = table
        .filter(_.secretId === secretId)
        .filter(_.deletedAt.isEmpty)
        .map(s => (s.vaultPathEncrypted, s.vaultPathKeyId, s.updatedAt))
        .update((vaultPathEncrypted, vaultPathKeyId, Instant.now()))

      run(q).map(_ == 1)
    }

  override def rotate(
      secretId: UUID,
      newVaultPathEncrypted: Array[Byte],
      newVaultPathKeyId: String
  ): IO[Boolean] =
    withContext("rotate") {
      IO.executionContext.flatMap { implicit ec =>
        val q: DBIO[Int] = for
          currentOpt <- table
            .filter(_.secretId === secretId)
            .filter(_.deletedAt.isEmpty)
            .map(_.version)
            .result
            .headOption
          updated <- currentOpt match
            case Some(currentVersion) =>
              table
                .filter(_.secretId === secretId)
                .map(s => (s.vaultPathEncrypted, s.vaultPathKeyId, s.version, s.rotatedAt, s.updatedAt))
                .update(
                  (
                    newVaultPathEncrypted,
                    newVaultPathKeyId,
                    currentVersion + 1,
                    Some(Instant.now()),
                    Instant.now()
                  )
                )
            case None => DBIO.successful(0)
        yield updated
        transactionally(q).map(_ == 1)
      }
    }

  override def deactivate(secretId: UUID): IO[Boolean] =
    withContext("deactivate") {
      val q = table
        .filter(_.secretId === secretId)
        .filter(_.deletedAt.isEmpty)
        .map(s => (s.isActive, s.updatedAt))
        .update((false, Instant.now()))

      run(q).map(_ == 1)
    }

  override def softDelete(secretId: UUID, deletedBy: String): IO[Boolean] =
    withContext("softDelete") {
      val q = table
        .filter(_.secretId === secretId)
        .filter(_.deletedAt.isEmpty)
        .map(s => (s.isActive, s.deletedAt, s.deletedBy, s.updatedAt))
        .update((false, Some(Instant.now()), Some(deletedBy), Instant.now()))

      run(q).map(_ == 1)
    }

  override def findDueForRotation(before: Instant, limit: Int): IO[Seq[SecretRow]] =
    withContext("findDueForRotation") {
      val query = table
        .filter(_.isActive === true)
        .filter(_.deletedAt.isEmpty)
        .filter(s => s.rotatedAt.isEmpty || s.rotatedAt < before)
        .sortBy(_.rotatedAt.asc.nullsFirst)
        .take(limit)
        .result

      run(query)
    }

  private object SecretInsert extends InsertActionRaw:

    protected def entityName = "SecretDB"

    protected def getId(entry: SecretRow): String = entry.secretId.getOrElse("none").toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      SecretRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      SecretRepository.this.fail(error)
