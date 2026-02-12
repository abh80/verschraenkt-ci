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
package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{ circeJsonTypeMapper, simpleStrListTypeMapper }
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.util.DomainRowMapper
import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Json }

import java.time.Instant

/** Database row representation of a Pipeline
  *
  * @param pipelineId
  *   Unique identifier for the pipeline
  * @param name
  *   Human-readable name of the pipeline
  * @param definition
  *   Complete pipeline definition stored as JSONB
  * @param version
  *   Current version number of the pipeline
  * @param createdAt
  *   Timestamp when the pipeline was created
  * @param updatedAt
  *   Timestamp when the pipeline was last updated
  * @param createdBy
  *   User who created the pipeline
  * @param labels
  *   Set of labels for categorizing and filtering pipelines
  * @param isActive
  *   Whether the pipeline is currently active; an inactive pipeline won't be triggered
  * @param deletedAt
  *   Optional timestamp when the pipeline was soft-deleted
  * @param deletedBy
  *   Optional user who deleted the pipeline
  */
final case class PipelineRow(
    pipelineId: PipelineId,
    name: String,
    definition: Json,
    version: Int,
    createdAt: Instant,
    updatedAt: Instant,
    createdBy: User,
    labels: List[String],
    isActive: Boolean,
    deletedAt: Option[Instant] = None,
    deletedBy: Option[User] = None
)

object PipelineRow extends DomainRowMapper[Pipeline, PipelineRow, (User, Int)]:
  /** Convert a domain Pipeline to a database row
    *
    * @param pipeline
    *   The domain Pipeline object to convert
    * @param createdBy
    *   User creating the pipeline
    * @param version
    *   Version number for this pipeline
    * @param ev
    *   Implicit Circe encoder for Pipeline
    * @return
    *   PipelineRow ready for database insertion
    */
  def fromDomain(pipeline: Pipeline, createdBy: User, version: Int)(using Encoder[Pipeline]): PipelineRow =
    PipelineRow(
      pipelineId = pipeline.id,
      name = extractPipelineName(pipeline),
      definition = pipeline.asJson,
      version = version,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      createdBy = createdBy,
      labels = pipeline.labels.toList,
      isActive = true,
      deletedAt = None,
      deletedBy = None
    )

  /** Convert a database row to a domain Pipeline
    *
    * @param row
    *   The database row to convert
    * @param ev
    *   Implicit Circe decoder for Pipeline
    * @return
    *   Either a decoding error or the domain Pipeline object
    */
  override def toDomain(row: PipelineRow)(using Decoder[Pipeline]): Either[io.circe.Error, Pipeline] =
    row.definition.as[Pipeline]

  /** Extract the pipeline name from the first workflow
    *
    * @param pipeline
    *   The pipeline to extract name from
    * @return
    *   The name of the first workflow in the pipeline
    */
  private def extractPipelineName(pipeline: Pipeline): String =
    pipeline.workflows.head.name

  /** Convert domain model to database row */
  override def fromDomain(domain: Pipeline, metadata: (User, Int))(using Encoder[Pipeline]): PipelineRow =
    fromDomain(domain, metadata._1, metadata._2)

/** Slick table definition for the pipelines table
  *
  * @param tag
  *   The Slick table tag
  */
class PipelineTable(tag: Tag) extends Table[PipelineRow](tag, "pipelines"):
  /** Default projection mapping all columns to PipelineRow case class */
  def * = (
    pipelineId,
    name,
    definition,
    version,
    createdAt,
    updatedAt,
    createdBy,
    labels,
    isActive,
    deletedAt,
    deletedBy
  ) <> ((PipelineRow.apply).tupled, PipelineRow.unapply)

  /** Primary key column for pipeline identifier */
  def pipelineId = column[PipelineId]("pipeline_id", O.PrimaryKey)

  /** Column for pipeline name */
  def name = column[String]("name")

  /** Column for pipeline definition stored as JSONB */
  def definition = column[Json]("definition")

  /** Column for current version number */
  def version = column[Int]("current_version")

  /** Column for creation timestamp */
  def createdAt = column[Instant]("created_at")

  /** Column for last update timestamp */
  def updatedAt = column[Instant]("updated_at")

  /** Column for user who created the pipeline */
  def createdBy = column[User]("created_by")

  /** Column for pipeline labels stored as array */
  def labels = column[List[String]]("labels")

  /** Column for pipeline active status (allows pipelines to be disabled temporarily rather than deletion) */
  def isActive = column[Boolean]("is_active")

  /** Column for soft deletion timestamp */
  def deletedAt = column[Option[Instant]]("deleted_at")

  /** Column for user who deleted the pipeline */
  def deletedBy = column[Option[User]]("deleted_by")
