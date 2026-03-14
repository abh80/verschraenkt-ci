package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.storageBackendMapper
import com.verschraenkt.ci.storage.db.codecs.Enums.StorageBackend
import slick.model.ForeignKeyAction.Cascade

import java.time.Instant
import java.util.UUID

/** Database row representation of a build artifact
  *
  * @param artifactId
  *   UUID v7 primary key, DB-generated
  * @param jobExecutionId
  *   The job execution that produced this artifact
  * @param executionId
  *   The pipeline execution this artifact belongs to
  * @param name
  *   Human-readable artifact name
  * @param path
  *   Original file path within the build
  * @param storageBackend
  *   Storage provider (s3, minio, gcs, azure_blob)
  * @param storageKey
  *   Key/path within the storage bucket
  * @param storageBucket
  *   Storage bucket name
  * @param sizeBytes
  *   Size of the artifact in bytes
  * @param contentType
  *   MIME content type
  * @param checksumSha256
  *   SHA-256 checksum of the artifact
  * @param uploadedAt
  *   When the artifact was uploaded
  * @param expiresAt
  *   When the artifact should be cleaned up
  * @param isPublic
  *   Whether the artifact is publicly accessible
  * @param madePublicAt
  *   When the artifact was made public
  * @param madePublicBy
  *   Who made the artifact public
  */
case class ArtifactRow(
    artifactId: Option[UUID],
    jobExecutionId: Long,
    executionId: Long,
    name: String,
    path: String,
    storageBackend: StorageBackend,
    storageKey: String,
    storageBucket: String,
    sizeBytes: Long,
    contentType: Option[String],
    checksumSha256: Option[String],
    uploadedAt: Instant,
    expiresAt: Option[Instant],
    isPublic: Boolean,
    madePublicAt: Option[Instant],
    madePublicBy: Option[String]
)

class ArtifactTable(tag: Tag) extends Table[ArtifactRow](tag, "artifacts"):
  def artifactId     = column[UUID]("artifact_id", O.PrimaryKey, O.AutoInc)
  def jobExecutionId = column[Long]("job_execution_id")
  def executionId    = column[Long]("execution_id")
  def name           = column[String]("name")
  def path           = column[String]("path")
  def storageBackend = column[StorageBackend]("storage_backend")
  def storageKey     = column[String]("storage_key")
  def storageBucket  = column[String]("storage_bucket")
  def sizeBytes      = column[Long]("size_bytes")
  def contentType    = column[Option[String]]("content_type")
  def checksumSha256 = column[Option[String]]("checksum_sha256")
  def uploadedAt     = column[Instant]("uploaded_at")
  def expiresAt      = column[Option[Instant]]("expires_at")
  def isPublic       = column[Boolean]("is_public")
  def madePublicAt   = column[Option[Instant]]("made_public_at")
  def madePublicBy   = column[Option[String]]("made_public_by")

  def * = (
    artifactId.?,
    jobExecutionId,
    executionId,
    name,
    path,
    storageBackend,
    storageKey,
    storageBucket,
    sizeBytes,
    contentType,
    checksumSha256,
    uploadedAt,
    expiresAt,
    isPublic,
    madePublicAt,
    madePublicBy
  ) <> (ArtifactRow.apply.tupled, ArtifactRow.unapply)

  def fk_jobExecutionId =
    foreignKey("artifacts_job_execution_id_fkey", jobExecutionId, TableQuery[JobExecutionTable])(
      _.jobExecutionId,
      onDelete = Cascade
    )
