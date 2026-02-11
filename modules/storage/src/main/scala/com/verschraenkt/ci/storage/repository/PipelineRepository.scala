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
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.simpleArrayColumnExtensionMethods
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.simpleStrListTypeMapper
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.{ JsonCodecs, User }
import com.verschraenkt.ci.storage.db.tables.{ PipelineRow, PipelineTable }
import com.verschraenkt.ci.storage.errors.StorageError
import org.postgresql.util.PSQLException

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
    with DatabaseOperations:

  import profile.api.*

  private val pipelines = TableQuery[PipelineTable]

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
    withOperation("findById") {
      val query = pipelines
        .filter(_.pipelineId === id)
        .filter(_.deletedAt.isEmpty)
        .result
        .headOption

      runAndConvertToPipelineDomain(query)
    }

  /** Find pipeline by ID including deleted ones */
  override def findByIdIncludingDeleted(id: PipelineId): IO[Option[Pipeline]] =
    withOperation("findByIdIncludingDeleted") {
      val query = pipelines
        .filter(_.pipelineId === id)
        .result
        .headOption

      runAndConvertToPipelineDomain(query)
    }

  /** Find all active pipelines with optional label filter */
  override def findActive(labels: Option[Set[String]], limit: Int): IO[Vector[Pipeline]] =
    withOperation("findActive") {
      // Base query: only active, non-deleted pipelines
      val baseQuery = pipelines
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
    withOperation("save") {

      val user = User(createdBy)
      val row  = PipelineRow.fromDomain(pipeline, user, version = 1)

      val insertAction = pipelines += row

      transactionally(insertAction)
        .map(_ => pipeline.id)
        .handleErrorWith {
          case e: PSQLException if isDuplicateKeyError(e) =>
            fail(StorageError.DuplicateKey("Pipeline", pipeline.id.value))
          case e: java.sql.SQLException =>
            fail(StorageError.ConnectionFailed(e))
          case e: Exception =>
            fail(StorageError.TransactionFailed(e))
        }
    }

  /** Update pipeline (creates new version) */
  override def update(pipeline: Pipeline, updatedBy: User, version: Int): IO[Int] =
    withOperation("update") {
      val q = pipelines
        .filter(_.pipelineId === pipeline.id)
        .filter(_.deletedAt.isEmpty)
        .update(PipelineRow.fromDomain(pipeline, updatedBy, version))
      run(q)
    }

  /** Soft-delete a pipeline */
  override def softDelete(id: PipelineId, deletedBy: User): IO[Boolean] =
    withOperation("softDelete") {
      val q = pipelines
        .filter(_.pipelineId === id)
        .filter(_.deletedAt.isEmpty)
        .map(p => (p.deletedAt, p.deletedBy))
        .update((Some(Instant.now()), Some(deletedBy)))

      run(q).map(_ == 1)
    }

  /** Helper method to check if exception is a duplicate key error */
  private def isDuplicateKeyError(e: PSQLException): Boolean =
    e.getSQLState == "23505" // PostgreSQL unique violation error code
