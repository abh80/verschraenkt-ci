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
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.given
import com.verschraenkt.ci.storage.db.codecs.User
import io.circe.syntax.*
import io.circe.{ Encoder, Json }
import slick.jdbc.PostgresProfile.api.*

import java.time.Instant

final case class PipelineRow(
    pipelineId: PipelineId,
    name: String,
    definition: Json,
    version: Int,
    createdAt: Instant,
    updatedAt: Instant,
    createdBy: User,
    labels: Set[String],
    isActive: Boolean,
    deletedAt: Option[Instant] = None,
    deletedBy: Option[User] = None
)

object PipelineRow:
  def fromDomain(pipeline: Pipeline, createdBy: User, version: Int)(using Encoder[Pipeline]): PipelineRow =
    PipelineRow(
      pipelineId = pipeline.id,
      name = extractPipelineName(pipeline),
      definition = pipeline.asJson,
      version = version,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      createdBy = createdBy,
      labels = pipeline.labels,
      isActive = true,
      deletedAt = None,
      deletedBy = None
    )

  private def extractPipelineName(pipeline: Pipeline): String =
    pipeline.workflows.head.name

class PipelineTable(tag: Tag) extends Table[PipelineRow](tag, "pipelines"):
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

  def pipelineId = column[PipelineId]("pipeline_id", O.PrimaryKey)

  def name = column[String]("name")

  def definition = column[Json]("definition")

  def version = column[Int]("current_version")

  def createdAt = column[Instant]("created_at")

  def updatedAt = column[Instant]("updated_at")

  def createdBy = column[User]("created_by")

  def labels = column[Set[String]]("labels")

  def isActive = column[Boolean]("is_active")

  def deletedAt = column[Option[Instant]]("deleted_at")

  def deletedBy = column[Option[User]]("deleted_by")
