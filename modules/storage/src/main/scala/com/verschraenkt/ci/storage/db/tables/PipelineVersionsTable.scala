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
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.circeJsonTypeMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.util.DomainRowMapper
import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Json }

import java.time.Instant

/** Database row representation for Pipeline table versions. Each entry represents a pipeline which existed at
  * some point.
  * @param pipelineId
  *   Unique identifier for the pipeline
  * @param version
  *   Version number of the pipeline
  * @param definition
  *   Complete pipeline definition stored as JSONB
  * @param createdAt
  *   Timestamp when the pipeline was created
  * @param createdBy
  *   User who created the pipeline
  * @param changeSummary
  *   Summary by the user
  */
final case class PipelineVersionsRow(
    pipelineId: PipelineId,
    version: Int,
    definition: Json,
    createdAt: Instant,
    createdBy: User,
    changeSummary: String
)

object PipelineVersionsRow extends DomainRowMapper[Pipeline, PipelineVersionsRow, (User, Int, String)]:
  /** Convert domain model to database row */
  override def fromDomain(domain: Pipeline, metadata: (User, Int, String))(using
      Encoder[Pipeline]
  ): PipelineVersionsRow =
    val (createdBy, version, summary) = metadata
    PipelineVersionsRow(
      domain.id,
      version,
      domain.asJson,
      Instant.now(),
      createdBy,
      changeSummary = summary
    )

  /** Convert database row to domain model */
  override def toDomain(row: PipelineVersionsRow)(using Decoder[Pipeline]): Either[io.circe.Error, Pipeline] =
    row.definition.as[Pipeline]

/** Slick table definition for the pipelines table
  *
  * @param tag
  *   The Slick table tag
  */
class PipelineVersionsTable(tag: Tag) extends Table[PipelineVersionsRow](tag, "pipeline_versions"):
  /** Primary key column for pipeline identifier */
  def pipelineId = column[PipelineId]("pipeline_id")

  /** Column for pipeline definition stored as JSONB */
  def definition = column[Json]("definition")

  /** Column for creation timestamp */
  def createdAt = column[Instant]("created_at")

  /** Column for user who created the pipeline */
  def createdBy = column[User]("created_by")

  /** Column for current version number */
  def version = column[Int]("version")

  /** Column for change summary */
  def changeSummary = column[String]("change_summary")

  /** Default mapping for mapping all columns to PipelineVersionsRow case class */
  def * = (
    pipelineId,
    version,
    definition,
    createdAt,
    createdBy,
    changeSummary
  ) <> (PipelineVersionsRow.apply.tupled, PipelineVersionsRow.unapply)

  /** Composite primary key on (pipeline_id, version) */
  def pk = primaryKey("pipeline_versions_pkey", (pipelineId, version))

  /** Foreign key reference to pipelines table */
  def pipeline = foreignKey("pipeline_versions_pipeline_id_fkey", pipelineId, TableQuery[PipelineTable])(
    _.pipelineId,
    onDelete = ForeignKeyAction.Cascade
  )
