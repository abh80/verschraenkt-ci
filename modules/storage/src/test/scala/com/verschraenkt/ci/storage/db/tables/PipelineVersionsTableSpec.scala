package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.JsonCodecs.given
import com.verschraenkt.ci.storage.db.codecs.User
import com.verschraenkt.ci.storage.fixtures.TestPipelines
import io.circe.syntax.*
import munit.FunSuite

import java.time.Instant

class PipelineVersionsTableSpec extends FunSuite:

  test("PipelineVersionsRow.fromDomain creates correct row") {
    val pipeline      = TestPipelines.simplePipeline
    val user          = TestPipelines.testUser
    val version       = 1
    val changeSummary = "initial version"

    val row = PipelineVersionsRow.fromDomain(pipeline, (user, version, changeSummary))

    assertEquals(row.pipelineId, pipeline.id)
    assertEquals(row.version, version)
    assertEquals(row.createdBy, user)
    assertEquals(row.changeSummary, changeSummary)
  }

  test("PipelineVersionsRow.fromDomain serializes definition to JSON") {
    val pipeline      = TestPipelines.pipelineWithLabels
    val user          = TestPipelines.testUser
    val changeSummary = "labels added"

    val row = PipelineVersionsRow.fromDomain(pipeline, (user, 1, changeSummary))

    val expectedJson = pipeline.asJson
    assertEquals(row.definition, expectedJson)
  }

  test("PipelineVersionsRow.fromDomain sets timestamps") {
    val pipeline      = TestPipelines.simplePipeline
    val user          = TestPipelines.testUser
    val changeSummary = "timestamp test"

    val before = Instant.now()
    val row    = PipelineVersionsRow.fromDomain(pipeline, (user, 1, changeSummary))
    val after  = Instant.now()

    assert(!row.createdAt.isBefore(before))
    assert(!row.createdAt.isAfter(after))
  }

  test("PipelineVersionsRow.fromDomain handles different users") {
    val pipeline      = TestPipelines.simplePipeline
    val user1         = User("user1")
    val user2         = User("user2")
    val changeSummary = "user change"

    val row1 = PipelineVersionsRow.fromDomain(pipeline, (user1, 1, changeSummary))
    val row2 = PipelineVersionsRow.fromDomain(pipeline, (user2, 1, changeSummary))

    assertEquals(row1.createdBy, user1)
    assertEquals(row2.createdBy, user2)
  }

  test("PipelineVersionsRow.fromDomain increments version correctly") {
    val pipeline      = TestPipelines.simplePipeline
    val user          = TestPipelines.testUser
    val changeSummary = "version increment"

    val row1 = PipelineVersionsRow.fromDomain(pipeline, (user, 1, changeSummary))
    val row2 = PipelineVersionsRow.fromDomain(pipeline, (user, 2, changeSummary))
    val row3 = PipelineVersionsRow.fromDomain(pipeline, (user, 3, changeSummary))

    assertEquals(row1.version, 1)
    assertEquals(row2.version, 2)
    assertEquals(row3.version, 3)
  }
