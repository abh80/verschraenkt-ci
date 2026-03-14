package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.StorageBackend
import com.verschraenkt.ci.storage.db.tables.ArtifactRow

import java.time.Instant

/** Reusable test data for artifact tests */
object TestArtifacts:

  private val snowflakeProvider = SnowflakeProvider.make(77)
  private var counter           = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  private def nextId = snowflakeProvider.nextId().value

  def artifact(
      jobExecutionId: Long = nextId,
      executionId: Long = nextId,
      name: String = s"artifact-${getCounter}"
  ): ArtifactRow =
    ArtifactRow(
      artifactId = None,
      jobExecutionId = jobExecutionId,
      executionId = executionId,
      name = name,
      path = s"build/output/$name.tar.gz",
      storageBackend = StorageBackend.S3,
      storageKey = s"artifacts/$executionId/$jobExecutionId/$name.tar.gz",
      storageBucket = "ci-artifacts",
      sizeBytes = 1024000L,
      contentType = Some("application/gzip"),
      checksumSha256 = Some("a" * 64),
      uploadedAt = Instant.now(),
      expiresAt = Some(Instant.now().plusSeconds(86400 * 30)),
      isPublic = false,
      madePublicAt = None,
      madePublicBy = None
    )

  def publicArtifact(
      jobExecutionId: Long = nextId,
      executionId: Long = nextId,
      name: String = s"artifact-public-${getCounter}"
  ): ArtifactRow =
    ArtifactRow(
      artifactId = None,
      jobExecutionId = jobExecutionId,
      executionId = executionId,
      name = name,
      path = s"build/output/$name.zip",
      storageBackend = StorageBackend.S3,
      storageKey = s"artifacts/$executionId/$jobExecutionId/$name.zip",
      storageBucket = "ci-artifacts-public",
      sizeBytes = 2048000L,
      contentType = Some("application/zip"),
      checksumSha256 = Some("b" * 64),
      uploadedAt = Instant.now().minusSeconds(3600),
      expiresAt = None,
      isPublic = true,
      madePublicAt = Some(Instant.now().minusSeconds(1800)),
      madePublicBy = Some("admin-user")
    )

  def minimalArtifact(
      jobExecutionId: Long = nextId,
      executionId: Long = nextId,
      name: String = s"artifact-minimal-${getCounter}"
  ): ArtifactRow =
    ArtifactRow(
      artifactId = None,
      jobExecutionId = jobExecutionId,
      executionId = executionId,
      name = name,
      path = s"output/$name.log",
      storageBackend = StorageBackend.S3,
      storageKey = s"logs/$executionId/$name.log",
      storageBucket = "ci-logs",
      sizeBytes = 512L,
      contentType = None,
      checksumSha256 = None,
      uploadedAt = Instant.now(),
      expiresAt = None,
      isPublic = false,
      madePublicAt = None,
      madePublicBy = None
    )

  def expiredArtifact(
      jobExecutionId: Long = nextId,
      executionId: Long = nextId,
      name: String = s"artifact-expired-${getCounter}"
  ): ArtifactRow =
    ArtifactRow(
      artifactId = None,
      jobExecutionId = jobExecutionId,
      executionId = executionId,
      name = name,
      path = s"build/output/$name.tar.gz",
      storageBackend = StorageBackend.S3,
      storageKey = s"artifacts/$executionId/$jobExecutionId/$name.tar.gz",
      storageBucket = "ci-artifacts",
      sizeBytes = 4096000L,
      contentType = Some("application/gzip"),
      checksumSha256 = Some("c" * 64),
      uploadedAt = Instant.now().minusSeconds(86400 * 60),
      expiresAt = Some(Instant.now().minusSeconds(86400)),
      isPublic = false,
      madePublicAt = None,
      madePublicBy = None
    )

  def minioArtifact(
      jobExecutionId: Long = nextId,
      executionId: Long = nextId,
      name: String = s"artifact-minio-${getCounter}"
  ): ArtifactRow =
    artifact(jobExecutionId, executionId, name).copy(
      storageBackend = StorageBackend.Minio,
      storageBucket = "local-artifacts"
    )
