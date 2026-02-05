package com.verschraenkt.ci.storage.errors

import com.verschraenkt.ci.core.utils.Location
import munit.FunSuite

class StorageErrorSpec extends FunSuite:

  test("NotFound error creation and message formatting") {
    val error = StorageError.NotFound("Pipeline", "pipeline-123")

    assertEquals(error.message, "Pipeline with id 'pipeline-123' not found")
    assertEquals(error.entity, "Pipeline")
    assertEquals(error.id, "pipeline-123")
    assertEquals(error.source, None)
    assertEquals(error.location, None)
    assertEquals(error.causeOpt, None)
  }

  test("DuplicateKey error creation and message formatting") {
    val error = StorageError.DuplicateKey("Pipeline", "pipeline-456")

    assertEquals(error.message, "Pipeline with id 'pipeline-456' already exists")
    assertEquals(error.entity, "Pipeline")
    assertEquals(error.id, "pipeline-456")
  }

  test("ConnectionFailed error with cause") {
    val cause = new java.sql.SQLException("Connection timeout")
    val error = StorageError.ConnectionFailed(cause)

    assert(error.message.contains("Connection timeout"))
    assertEquals(error.causeOpt, Some(cause))
  }

  test("TransactionFailed error with cause") {
    val cause = new RuntimeException("Deadlock detected")
    val error = StorageError.TransactionFailed(cause)

    assert(error.message.contains("Deadlock detected"))
    assertEquals(error.causeOpt, Some(cause))
  }

  test("DecodingFailed error creation") {
    val error = StorageError.DecodingFailed("Invalid JSON structure")

    assertEquals(error.message, "JSON decoding failed: Invalid JSON structure")
    assertEquals(error.msg, "Invalid JSON structure")
  }

  test("ObjectStoreError error creation") {
    val cause = new Exception("Access denied")
    val error = StorageError.ObjectStoreError("upload", "artifacts/build.zip", cause)

    assert(error.message.contains("upload"))
    assert(error.message.contains("artifacts/build.zip"))
    assert(error.message.contains("Access denied"))
    assertEquals(error.operation, "upload")
    assertEquals(error.key, "artifacts/build.zip")
  }

  test("NotFound withSource method") {
    val error      = StorageError.NotFound("Pipeline", "id-1")
    val withSource = error.withSource("PipelineRepository")

    assertEquals(withSource.source, Some("PipelineRepository"))
    assertEquals(withSource.entity, "Pipeline")
    assertEquals(withSource.id, "id-1")
  }

  test("NotFound withLocation method") {
    val error        = StorageError.NotFound("Job", "job-1")
    val location     = Location("JobRepository.scala", Some(42))
    val withLocation = error.withLocation(location)

    assertEquals(withLocation.location, Some(location))
    assertEquals(withLocation.entity, "Job")
  }

  test("NotFound withCause method") {
    val error     = StorageError.NotFound("Workflow", "wf-1")
    val cause     = new Exception("Database unreachable")
    val withCause = error.withCause(cause)

    assertEquals(withCause.causeOpt, Some(cause))
  }

  test("DuplicateKey withSource method") {
    val error      = StorageError.DuplicateKey("Pipeline", "pipe-1")
    val withSource = error.withSource("save operation")

    assertEquals(withSource.source, Some("save operation"))
  }

  test("DuplicateKey withLocation method") {
    val error        = StorageError.DuplicateKey("Pipeline", "pipe-1")
    val location     = Location("PipelineRepository.scala", Some(100))
    val withLocation = error.withLocation(location)

    assertEquals(withLocation.location, Some(location))
  }

  test("ConnectionFailed withSource method") {
    val cause      = new java.sql.SQLException("Timeout")
    val error      = StorageError.ConnectionFailed(cause)
    val withSource = error.withSource("DatabaseModule")

    assertEquals(withSource.source, Some("DatabaseModule"))
  }

  test("ConnectionFailed withLocation method") {
    val cause        = new java.sql.SQLException("Timeout")
    val error        = StorageError.ConnectionFailed(cause)
    val location     = Location("DatabaseModule.scala", Some(50))
    val withLocation = error.withLocation(location)

    assertEquals(withLocation.location, Some(location))
  }

  test("TransactionFailed withCause method") {
    val originalCause = new RuntimeException("Original error")
    val error         = StorageError.TransactionFailed(originalCause)
    val newCause      = new RuntimeException("New error")
    val withCause     = error.withCause(newCause)

    assertEquals(withCause.causeOpt, Some(newCause))
  }

  test("DecodingFailed withMessage method") {
    val error       = StorageError.DecodingFailed("Original message")
    val withMessage = error.withMessage("New message")

    assertEquals(withMessage.msg, "New message")
  }

  test("Error chaining - multiple context enrichments") {
    val location = Location("PipelineRepository.scala", Some(75))
    val error = StorageError
      .NotFound("Pipeline", "missing-pipeline")
      .withSource("PipelineRepository")
      .withLocation(location)

    assertEquals(error.source, Some("PipelineRepository"))
    assertEquals(error.location, Some(location))
    assertEquals(error.entity, "Pipeline")
    assertEquals(error.id, "missing-pipeline")
  }
