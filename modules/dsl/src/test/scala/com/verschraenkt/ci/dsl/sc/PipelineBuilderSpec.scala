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
package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*

class PipelineBuilderSpec extends FunSuite:

  private def createDummyWorkflow(name: String, jobId: String): Workflow =
    Workflow(name, NonEmptyVector.one(createDummyJob(jobId)))

  private def createDummyJob(jobId: String): Job =
    Job.one(id = JobId(jobId), step = Step.Run(Command.Shell("echo 'dummy'"))(using StepMeta()))

  test("PipelineBuilder should build a pipeline with specified properties") {
    val pipelineBuilder = new PipelineBuilder(PipelineId("my-pipeline"))
    pipelineBuilder.addWorkflow(createDummyWorkflow("workflow1", "job1"))
    pipelineBuilder.setConcurrency("pipeline-group")
    pipelineBuilder.addLabels("prod", "backend")
    pipelineBuilder.setTimeout(30.minutes)

    val pipeline = pipelineBuilder.build()

    assertEquals(pipeline.id, PipelineId("my-pipeline"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.concurrencyGroup, Some("pipeline-group"))
    assertEquals(pipeline.labels, Set("prod", "backend"))
    assertEquals(pipeline.timeout, Some(30.minutes))
  }

  test("PipelineBuilder should build a pipeline with default properties") {
    val pipelineBuilder = new PipelineBuilder(PipelineId("default-pipeline"))
    pipelineBuilder.addWorkflow(createDummyWorkflow("workflow-default", "job-default"))

    val pipeline = pipelineBuilder.build()

    assertEquals(pipeline.id, PipelineId("default-pipeline"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.concurrencyGroup, None)
    assertEquals(pipeline.labels, Set.empty[String])
    assertEquals(pipeline.timeout, None)
  }

  test("PipelineBuilder should require at least one workflow") {
    val pipelineBuilder = new PipelineBuilder(PipelineId("empty-pipeline"))
    intercept[IllegalArgumentException] {
      pipelineBuilder.build()
    }
  }

  test("PipelineBuilder should handle multiple workflows") {
    val pipelineBuilder = new PipelineBuilder(PipelineId("multi-workflow-pipeline"))
    pipelineBuilder.addWorkflow(createDummyWorkflow("workflow-a", "job-a"))
    pipelineBuilder.addWorkflow(createDummyWorkflow("workflow-b", "job-b"))

    val pipeline = pipelineBuilder.build()
    assertEquals(pipeline.workflows.length, 2)
  }
