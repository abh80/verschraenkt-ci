package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.fixtures.TestPipelines
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class PipelineTableSpec extends FunSuite:

  test("PipelineRow.fromDomain creates correct row") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser
    val version  = 1

    val row = PipelineRow.fromDomain(pipeline, user, version)

    assertEquals(row.pipelineId, pipeline.id)
    assertEquals(row.version, version)
    assertEquals(row.createdBy, user)
    assertEquals(row.labels, pipeline.labels)
    assertEquals(row.isActive, true)
    assertEquals(row.deletedAt, None)
    assertEquals(row.deletedBy, None)
  }

  test("PipelineRow.fromDomain extracts pipeline name from first workflow") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.name, "simple-workflow")
  }

  test("PipelineRow.fromDomain serializes definition to JSON") {
    val pipeline = TestPipelines.pipelineWithLabels
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    val expectedJson = pipeline.asJson
    assertEquals(row.definition, expectedJson)
  }

  test("PipelineRow.fromDomain sets labels correctly") {
    val pipeline = TestPipelines.pipelineWithLabels
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.labels, Set("production", "automated", "critical"))
  }

  test("PipelineRow.fromDomain handles empty labels") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.labels, Set.empty[String])
  }

  test("PipelineRow.fromDomain sets timestamps") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val before = Instant.now()
    val row    = PipelineRow.fromDomain(pipeline, user, 1)
    val after  = Instant.now()

    assert(!row.createdAt.isBefore(before))
    assert(!row.createdAt.isAfter(after))
    assert(!row.updatedAt.isBefore(before))
    assert(!row.updatedAt.isAfter(after))
  }

  test("PipelineRow.fromDomain sets isActive to true by default") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.isActive, true)
  }

  test("PipelineRow.fromDomain sets deletedAt to None") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.deletedAt, None)
  }

  test("PipelineRow.fromDomain sets deletedBy to None") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.deletedBy, None)
  }

  test("PipelineRow.fromDomain handles multiple workflows") {
    val pipeline = TestPipelines.pipelineWithMultipleWorkflows
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    // Should extract name from first workflow
    assertEquals(row.name, "build-workflow")
  }

  test("PipelineRow.fromDomain increments version correctly") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val row1 = PipelineRow.fromDomain(pipeline, user, 1)
    val row2 = PipelineRow.fromDomain(pipeline, user, 2)
    val row3 = PipelineRow.fromDomain(pipeline, user, 3)

    assertEquals(row1.version, 1)
    assertEquals(row2.version, 2)
    assertEquals(row3.version, 3)
  }

  test("PipelineRow.fromDomain handles different users") {
    val pipeline = TestPipelines.simplePipeline
    val user1    = User("user1")
    val user2    = User("user2")

    val row1 = PipelineRow.fromDomain(pipeline, user1, 1)
    val row2 = PipelineRow.fromDomain(pipeline, user2, 1)

    assertEquals(row1.createdBy, user1)
    assertEquals(row2.createdBy, user2)
  }

  test("PipelineRow with complex pipeline") {
    val pipeline = TestPipelines.complexPipeline
    val user     = TestPipelines.testUser

    val row = PipelineRow.fromDomain(pipeline, user, 1)

    assertEquals(row.pipelineId, pipeline.id)
    assertEquals(row.labels, Set("docker", "build"))
    assert(row.definition.noSpaces.contains("docker"))
  }
