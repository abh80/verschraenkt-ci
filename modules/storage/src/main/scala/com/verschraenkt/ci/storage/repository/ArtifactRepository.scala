package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ ArtifactRow, ArtifactTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant
import java.util.UUID

sealed trait IArtifactRepository:
  /** Create a new artifact */
  def create(artifact: ArtifactRow): IO[UUID]

  /** Find an artifact by its ID */
  def findById(artifactId: UUID): IO[Option[ArtifactRow]]

  /** Find all artifacts for a job execution */
  def findByJobExecutionId(jobExecutionId: Long): IO[Seq[ArtifactRow]]

  /** Find all artifacts for a pipeline execution */
  def findByExecutionId(executionId: Long, limit: Int = 100): IO[Seq[ArtifactRow]]

  /** Find all public artifacts for an execution */
  def findPublicByExecutionId(executionId: Long, limit: Int = 100): IO[Seq[ArtifactRow]]

  /** Find expired artifacts that should be cleaned up */
  def findExpired(at: Instant = Instant.now(), limit: Int = 100): IO[Seq[ArtifactRow]]

  /** Make an artifact public */
  def makePublic(artifactId: UUID, madePublicBy: String): IO[Boolean]

  /** Delete an artifact by ID */
  def delete(artifactId: UUID): IO[Boolean]

class ArtifactRepository(val dbModule: DatabaseModule)
    extends IArtifactRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[ArtifactRow]:
  import PostgresProfile.api.*

  override def create(artifact: ArtifactRow): IO[UUID] =
    withContext("create") {
      val applicationContext = summon[ApplicationContext]

      val insertAction = table.returning(table.map(_.artifactId)) += artifact
      ArtifactInsert.runTransactionWithDefaultFailureHandle(
        transactionally(insertAction)
      )(using applicationContext, artifact)
    }

  override def findById(artifactId: UUID): IO[Option[ArtifactRow]] =
    withContext("findById") {
      val query = table
        .filter(_.artifactId === artifactId)
        .result
        .headOption

      run(query)
    }

  override def findByJobExecutionId(jobExecutionId: Long): IO[Seq[ArtifactRow]] =
    withContext("findByJobExecutionId") {
      val query = table
        .filter(_.jobExecutionId === jobExecutionId)
        .sortBy(_.uploadedAt.desc)
        .result

      run(query)
    }

  override def findByExecutionId(executionId: Long, limit: Int): IO[Seq[ArtifactRow]] =
    withContext("findByExecutionId") {
      val query = table
        .filter(_.executionId === executionId)
        .sortBy(_.uploadedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findPublicByExecutionId(executionId: Long, limit: Int): IO[Seq[ArtifactRow]] =
    withContext("findPublicByExecutionId") {
      val query = table
        .filter(_.executionId === executionId)
        .filter(_.isPublic === true)
        .sortBy(_.uploadedAt.desc)
        .take(limit)
        .result

      run(query)
    }

  override def findExpired(at: Instant, limit: Int): IO[Seq[ArtifactRow]] =
    withContext("findExpired") {
      val query = table
        .filter(_.expiresAt.isDefined)
        .filter(_.expiresAt < at)
        .sortBy(_.expiresAt.asc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  override def makePublic(artifactId: UUID, madePublicBy: String): IO[Boolean] =
    withContext("makePublic") {
      val q = table
        .filter(_.artifactId === artifactId)
        .map(a => (a.isPublic, a.madePublicAt, a.madePublicBy))
        .update((true, Some(Instant.now()), Some(madePublicBy)))

      run(q).map(_ == 1)
    }

  override def delete(artifactId: UUID): IO[Boolean] =
    withContext("delete") {
      val q = table
        .filter(_.artifactId === artifactId)
        .delete

      run(q).map(_ == 1)
    }

  override def table: TableQuery[ArtifactTable] = TableQuery[ArtifactTable]

  private object ArtifactInsert extends InsertActionRaw:

    protected def entityName = "ArtifactDB"

    protected def getId(entry: ArtifactRow): String = entry.artifactId.getOrElse("none").toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      ArtifactRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      ArtifactRepository.this.fail(error)
