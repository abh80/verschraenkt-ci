/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.db.tables.{ PipelineVersionsRow, PipelineVersionsTable }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

trait IPipelineVersionsRepository:
  /** Find a specific version of a pipeline */
  def findByIdAndVersion(id: PipelineId, version: Int): IO[Option[Pipeline]]

  /** Create a new version entry */
  def create(pipeline: Pipeline, version: Int, createdBy: User, changeSummary: String): IO[Unit]

  /** Find all versions of a pipeline, ordered by version desc */
  def findAllVersionsByPipelineId(id: PipelineId): IO[Seq[Int]]

  /** Find the latest version number for a pipeline */
  def findLatestVersionByPipelineId(id: PipelineId): IO[Option[Int]]

  /** Find versions in a range (inclusive) */
  def findVersionsInRange(id: PipelineId, fromVersion: Int, toVersion: Int): IO[Seq[Int]]

  /** Count total versions for a pipeline */
  def countVersionsByPipelineId(id: PipelineId): IO[Int]

  /** Check if a specific version exists */
  def existsVersion(id: PipelineId, version: Int): IO[Boolean]

  /** Get paginated version history */
  def listVersionsPaginated(id: PipelineId, offset: Int, limit: Int): IO[Seq[Int]]

class PipelineVersionsRepository(protected val dbModule: DatabaseModule)
    extends IPipelineVersionsRepository
    with StorageContext
    with DatabaseOperations
    with TableCast[PipelineVersionsRow]:
  import profile.api.*

  /** Helper method to run a query and convert the result to domain model */
  private def runAndConvertToPipelineDomain(query: DBIO[Option[PipelineVersionsRow]]): IO[Option[Pipeline]] =
    run(query).flatMap {
      case Some(row) =>
        PipelineVersionsRow.toDomain(row) match
          case Right(p)    => IO.pure(Some(p))
          case Left(error) => IO.raiseError(StorageError.DeserializationFailed(error))
      case None => IO.pure(None)
    }

  /** Find a specific version of a pipeline */
  override def findByIdAndVersion(id: PipelineId, version: Int): IO[Option[Pipeline]] =
    withContext("findByIdAndVersion") {
      runAndConvertToPipelineDomain(
        byPipelineId(id).filter(_.version === version).result.headOption
      )
    }

  /** Create a new version entry */
  override def create(pipeline: Pipeline, version: Int, createdBy: User, changeSummary: String): IO[Unit] =
    withContext("create") {
      PipelineInsert.insert(pipeline, (createdBy, version, changeSummary)).void
    }

  /** Find all versions of a pipeline, ordered by version desc */
  override def findAllVersionsByPipelineId(id: PipelineId): IO[Seq[Int]] =
    withContext("findAllVersionsByPipelineId") {
      run(
        byPipelineId(id)
          .sortBy(_.version.desc)
          .map(_.version)
          .result
      )
    }

  /** Find the latest version number for a pipeline */
  override def findLatestVersionByPipelineId(id: PipelineId): IO[Option[Int]] =
    withContext("findLatestVersionByPipelineId") {
      run(
        byPipelineId(id).sortBy(_.version.desc).map(_.version).result.headOption
      )
    }

  /** Find versions in a range (inclusive) */
  override def findVersionsInRange(id: PipelineId, fromVersion: Int, toVersion: Int): IO[Seq[Int]] =
    withContext("findVersionsInRange") {
      run(
        byPipelineId(id).map(_.version).filter(_ >= fromVersion).filter(_ <= toVersion).result
      )
    }

  /** Count total versions for a pipeline */
  override def countVersionsByPipelineId(id: PipelineId): IO[Int] =
    withContext("countVersionsByPipelineId") {
      run(byPipelineId(id).countDistinct.result)
    }

  /** Check if a specific version exists */
  override def existsVersion(id: PipelineId, version: Int): IO[Boolean] =
    withContext("existsVersion") {
      run(
        table.filter(p => p.pipelineId === id && p.version === version).result.headOption
      ).map(_.isDefined)
    }

  /** Get paginated version history */
  override def listVersionsPaginated(id: PipelineId, offset: Int, limit: Int): IO[Seq[Int]] =
    withContext("listVersionsPaginated") {
      run(
        byPipelineId(id).map(_.version).sortBy(_.desc).drop(offset).take(limit).result
      )
    }

  override protected def table: TableQuery[PipelineVersionsTable] = TableQuery[PipelineVersionsTable]

  private def byPipelineId(id: PipelineId) =
    table.filter(_.pipelineId === id)

  private object PipelineInsert extends InsertAction[Pipeline, (User, Int, String)]:
    protected def mapper = PipelineVersionsRow

    protected def entityName = "PipelineVersions"

    protected def getId(domain: Pipeline) = domain.id.value

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      PipelineVersionsRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      PipelineVersionsRepository.this.fail(error)
