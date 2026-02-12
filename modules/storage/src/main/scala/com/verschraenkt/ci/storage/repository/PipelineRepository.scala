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
import cats.syntax.traverse.given
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{
  simpleArrayColumnExtensionMethods,
  simpleStrListTypeMapper
}
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.{ JsonCodecs, User }
import com.verschraenkt.ci.storage.db.tables.{ PipelineRow, PipelineTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

import java.time.Instant

/** Repository for pipeline persistence operations */
trait IPipelineRepository:
  /** Find a pipeline by ID (only non-deleted) */
  def findById(id: PipelineId): IO[Option[Pipeline]]

  /** Find pipeline by ID including deleted ones */
  def findByIdIncludingDeleted(id: PipelineId): IO[Option[Pipeline]]

  /** Find all active pipelines with optional label filter */
  def findActive(labels: Option[Set[String]] = None, limit: Int = 100): IO[Vector[Pipeline]]

  /** Save a new pipeline or create new version */
  def save(pipeline: Pipeline, createdBy: String): IO[PipelineId]

  /** Update pipeline (creates new version) */
  def update(pipeline: Pipeline, updatedBy: User, version: Int): IO[Int]

  /** Soft-delete a pipeline */
  def softDelete(id: PipelineId, deletedBy: User): IO[Boolean]

/** Slick-based implementation */
class PipelineRepository(
    protected val dbModule: DatabaseModule
) extends IPipelineRepository
    with StorageContext
    with DatabaseOperations
    with TableCast[PipelineRow]:

  import profile.api.*

  /** Helper method to run a query and convert the result to domain model */
  private def runAndConvertToPipelineDomain(query: DBIO[Option[PipelineRow]]): IO[Option[Pipeline]] =
    run(query).flatMap {
      case Some(row) =>
        PipelineRow.toDomain(row) match
          case Right(p)    => IO.pure(Some(p))
          case Left(error) => IO.raiseError(StorageError.DeserializationFailed(error))
      case None => IO.pure(None)
    }

  /** Find a pipeline by ID (only non-deleted) */
  override def findById(id: PipelineId): IO[Option[Pipeline]] =
    withContext("findById") {
      val query = table
        .filter(_.pipelineId === id)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      runAndConvertToPipelineDomain(query)
    }

  /** Find pipeline by ID including deleted ones */
  override def findByIdIncludingDeleted(id: PipelineId): IO[Option[Pipeline]] =
    withContext("findByIdIncludingDeleted") {
      val query = table
        .filter(_.pipelineId === id)
        .result
        .headOption

      runAndConvertToPipelineDomain(query)
    }

  /** Find all active table with optional label filter */
  override def findActive(labels: Option[Set[String]], limit: Int): IO[Vector[Pipeline]] =
    withContext("findActive") {
      // Base query: only active, non-deleted table
      val baseQuery = table
        .filter(_.isActive === true)
        .filter(_.deletedAt.isEmpty)

      // Apply label filtering if requested (subset match: pipeline must contain all requested labels)
      val labelFilteredQuery: Query[PipelineTable, PipelineRow, Seq] = labels match
        case Some(requestedLabels) if requestedLabels.nonEmpty =>
          baseQuery.filter(row => row.labels @> requestedLabels.toList)
        case _ => baseQuery

      // Sort by updated_at descending and apply limit
      val query = labelFilteredQuery
        .sortBy(_.updatedAt.desc)
        .take(limit)
        .result

      run(query).flatMap { (seq: Seq[PipelineRow]) =>
        seq.toVector.traverse(row =>
          PipelineRow.toDomain(row) match
            case Right(p)    => IO.pure(p)
            case Left(error) => IO.raiseError(StorageError.DeserializationFailed(error))
        )
      }
    }

  /** Save a new pipeline or create new version */
  override def save(pipeline: Pipeline, createdBy: String): IO[PipelineId] =
    withContext("save") {
      val user = User(createdBy)
      PipelineInsert.insert(pipeline, (user, 1)).map(_.id)
    }

  /** Update pipeline (creates new version) */
  override def update(pipeline: Pipeline, updatedBy: User, version: Int): IO[Int] =
    withContext("update") {
      val q = table
        .filter(_.pipelineId === pipeline.id)
        .filter(_.deletedAt.isEmpty)
        .update(PipelineRow.fromDomain(pipeline, updatedBy, version))
      run(q)
    }

  /** Soft-delete a pipeline */
  override def softDelete(id: PipelineId, deletedBy: User): IO[Boolean] =
    withContext("softDelete") {
      val q = table
        .filter(_.pipelineId === id)
        .filter(_.deletedAt.isEmpty)
        .map(p => (p.deletedAt, p.deletedBy))
        .update((Some(Instant.now()), Some(deletedBy)))

      run(q).map(_ == 1)
    }

  override protected def table: TableQuery[PipelineTable] = TableQuery[PipelineTable]

  private object PipelineInsert extends InsertAction[Pipeline, (User, Int)]:
    protected def mapper = PipelineRow

    protected def entityName = "Pipeline"

    protected def getId(domain: Pipeline) = domain.id.value

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      PipelineRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      PipelineRepository.this.fail(error)
