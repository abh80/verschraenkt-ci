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
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.{ JsonCodecs, User }
import com.verschraenkt.ci.storage.db.tables.{ PipelineRow, PipelineTable }
import com.verschraenkt.ci.storage.errors.StorageError
import org.postgresql.util.PSQLException

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
  def update(pipeline: Pipeline, updatedBy: String): IO[Int]

  /** Soft-delete a pipeline */
  def softDelete(id: PipelineId, deletedBy: String): IO[Boolean]

  /** Get all versions of a pipeline */
  def getVersionHistory(id: PipelineId): IO[Vector[PipelineRow]]

/** Slick-based implementation */
class PipelineRepository(
    protected val dbModule: DatabaseModule
) extends IPipelineRepository
    with StorageContext
    with DatabaseOperations:

  import profile.api.*

  private val pipelines = TableQuery[PipelineTable]

  /** Find a pipeline by ID (only non-deleted) */
  override def findById(id: PipelineId): IO[Option[Pipeline]] = ???

  /** Find pipeline by ID including deleted ones */
  override def findByIdIncludingDeleted(id: PipelineId): IO[Option[Pipeline]] = ???

  /** Find all active pipelines with optional label filter */
  override def findActive(labels: Option[Set[String]], limit: Int): IO[Vector[Pipeline]] = ???

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
  override def update(pipeline: Pipeline, updatedBy: String): IO[Int] = ???

  /** Soft-delete a pipeline */
  override def softDelete(id: PipelineId, deletedBy: String): IO[Boolean] = ???

  /** Get all versions of a pipeline */
  override def getVersionHistory(id: PipelineId): IO[Vector[PipelineRow]] = ???

  /** Helper method to check if exception is a duplicate key error */
  private def isDuplicateKeyError(e: PSQLException): Boolean =
    e.getSQLState == "23505" // PostgreSQL unique violation error code
