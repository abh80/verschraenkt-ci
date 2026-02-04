package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
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

  def version = column[Int]("version")

  def createdAt = column[Instant]("created_at")

  def updatedAt = column[Instant]("updated_at")

  def createdBy = column[User]("created_by")

  def labels = column[Set[String]]("labels")

  def isActive = column[Boolean]("is_active")

  def deletedAt = column[Option[Instant]]("deleted_at")

  def deletedBy = column[Option[User]]("deleted_by")
