package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.Enums.StorageBackend
import com.verschraenkt.ci.storage.fixtures.TestArtifacts
import munit.FunSuite

import java.time.Instant

class ArtifactTableSpec extends FunSuite:

  test("ArtifactRow creates correct row with all fields") {
    val row = TestArtifacts.artifact()

    assertEquals(row.artifactId, None)
    assert(row.jobExecutionId > 0)
    assert(row.executionId > 0)
    assert(row.name.startsWith("artifact-"))
    assert(row.path.nonEmpty)
    assertEquals(row.storageBackend, StorageBackend.S3)
    assert(row.storageKey.nonEmpty)
    assert(row.storageBucket.nonEmpty)
    assert(row.sizeBytes > 0)
    assert(row.contentType.isDefined)
    assert(row.checksumSha256.isDefined)
    assertEquals(row.isPublic, false)
    assertEquals(row.madePublicAt, None)
    assertEquals(row.madePublicBy, None)
  }

  test("ArtifactRow handles public artifact with audit fields") {
    val row = TestArtifacts.publicArtifact()

    assertEquals(row.isPublic, true)
    assert(row.madePublicAt.isDefined)
    assert(row.madePublicBy.isDefined)
    assertEquals(row.madePublicBy, Some("admin-user"))
  }

  test("ArtifactRow handles minimal artifact without optional fields") {
    val row = TestArtifacts.minimalArtifact()

    assertEquals(row.contentType, None)
    assertEquals(row.checksumSha256, None)
    assertEquals(row.expiresAt, None)
    assertEquals(row.isPublic, false)
  }

  test("ArtifactRow handles expired artifact") {
    val row = TestArtifacts.expiredArtifact()

    assert(row.expiresAt.isDefined)
    assert(row.expiresAt.get.isBefore(Instant.now()))
  }

  test("ArtifactRow handles different storage backends") {
    val s3    = TestArtifacts.artifact()
    val minio = TestArtifacts.minioArtifact()

    assertEquals(s3.storageBackend, StorageBackend.S3)
    assertEquals(minio.storageBackend, StorageBackend.Minio)
  }

  test("ArtifactRow handles checksum format") {
    val row = TestArtifacts.artifact()

    assert(row.checksumSha256.isDefined)
    assertEquals(row.checksumSha256.get.length, 64)
  }

  test("ArtifactRow handles size_bytes constraint") {
    val row = TestArtifacts.artifact()

    assert(row.sizeBytes >= 0)
  }

  test("ArtifactRow default values for optional fields") {
    val row = ArtifactRow(
      artifactId = None,
      jobExecutionId = 1L,
      executionId = 1L,
      name = "test",
      path = "test/path",
      storageBackend = StorageBackend.S3,
      storageKey = "key",
      storageBucket = "bucket",
      sizeBytes = 0L,
      contentType = None,
      checksumSha256 = None,
      uploadedAt = Instant.now(),
      expiresAt = None,
      isPublic = false,
      madePublicAt = None,
      madePublicBy = None
    )

    assertEquals(row.artifactId, None)
    assertEquals(row.contentType, None)
    assertEquals(row.checksumSha256, None)
    assertEquals(row.expiresAt, None)
    assertEquals(row.madePublicAt, None)
    assertEquals(row.madePublicBy, None)
  }

  test("ArtifactRow preserves specific name and path") {
    val row = TestArtifacts.artifact(name = "my-build")

    assertEquals(row.name, "my-build")
    assert(row.path.contains("my-build"))
  }
