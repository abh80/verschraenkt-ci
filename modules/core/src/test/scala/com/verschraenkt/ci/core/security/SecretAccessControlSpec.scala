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
package com.verschraenkt.ci.core.security

import com.verschraenkt.ci.core.model.{ JobId, PipelineId }
import munit.FunSuite

class SecretAccessControlSpec extends FunSuite:

  private val jobA          = JobId("job-a")
  private val jobB          = JobId("job-b")
  private val pipelineX     = PipelineId("pipeline-x")
  private val pipelineY     = PipelineId("pipeline-y")
  private val workflowAlpha = "workflow-alpha"
  private val workflowBeta  = "workflow-beta"

  private def context(
      job: JobId = jobA,
      workflow: String = workflowAlpha,
      pipeline: PipelineId = pipelineX,
      timestamp: Long = System.currentTimeMillis() / 1000
  ) = SecretAccessContext(job, workflow, pipeline, timestamp)

  test("global scope - allows access from any job") {
    val accessControl = SecretAccessControl.global
    val result        = SecretAccessControl.validateAccess("secret1", accessControl, context())
    assert(result.isRight)
  }

  test("job scope - allows access from specified job") {
    val accessControl = SecretAccessControl.forJob(Set(jobA))
    val result        = SecretAccessControl.validateAccess("secret1", accessControl, context(job = jobA))
    assert(result.isRight)
  }

  test("job scope - denies access from non-specified job") {
    val accessControl = SecretAccessControl.forJob(Set(jobA))
    val result        = SecretAccessControl.validateAccess("secret1", accessControl, context(job = jobB))
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("does not have access"))
  }

  test("workflow scope - allows access from specified workflow") {
    val accessControl = SecretAccessControl.forWorkflow(Set(workflowAlpha))
    val result =
      SecretAccessControl.validateAccess("secret1", accessControl, context(workflow = workflowAlpha))
    assert(result.isRight)
  }

  test("workflow scope - denies access from non-specified workflow") {
    val accessControl = SecretAccessControl.forWorkflow(Set(workflowAlpha))
    val result =
      SecretAccessControl.validateAccess("secret1", accessControl, context(workflow = workflowBeta))
    assert(result.isLeft)
  }

  test("pipeline scope - allows access from specified pipeline") {
    val accessControl = SecretAccessControl.forPipeline(Set(pipelineX))
    val result = SecretAccessControl.validateAccess("secret1", accessControl, context(pipeline = pipelineX))
    assert(result.isRight)
  }

  test("pipeline scope - denies access from non-specified pipeline") {
    val accessControl = SecretAccessControl.forPipeline(Set(pipelineX))
    val result = SecretAccessControl.validateAccess("secret1", accessControl, context(pipeline = pipelineY))
    assert(result.isLeft)
  }

  test("expired secret - denies access") {
    val pastTimestamp = System.currentTimeMillis() / 1000 - 3600 // 1 hour ago
    val accessControl = SecretAccessControl(
      scope = SecretScope.Global,
      expiresAt = Some(pastTimestamp)
    )
    val result = SecretAccessControl.validateAccess("secret1", accessControl, context())
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("expired"))
  }

  test("non-expired secret - allows access") {
    val futureTimestamp = System.currentTimeMillis() / 1000 + 3600 // 1 hour from now
    val accessControl = SecretAccessControl(
      scope = SecretScope.Global,
      expiresAt = Some(futureTimestamp)
    )
    val result = SecretAccessControl.validateAccess("secret1", accessControl, context())
    assert(result.isRight)
  }

  test("empty allowedJobs means allow all jobs within scope") {
    val accessControl = SecretAccessControl(
      scope = SecretScope.Job,
      allowedJobs = Set.empty
    )
    val result = SecretAccessControl.validateAccess("secret1", accessControl, context(job = jobB))
    assert(result.isRight)
  }

  test("hierarchical validation - global allows all") {
    val accessControl = SecretAccessControl.global
    val result        = SecretAccessControl.validateAccessHierarchical("secret1", accessControl, context())
    assert(result.isRight)
  }

  test("hierarchical validation - pipeline scope checks pipeline") {
    val accessControl = SecretAccessControl.forPipeline(Set(pipelineX))
    val resultOk =
      SecretAccessControl.validateAccessHierarchical("secret1", accessControl, context(pipeline = pipelineX))
    assert(resultOk.isRight)

    val resultFail =
      SecretAccessControl.validateAccessHierarchical("secret1", accessControl, context(pipeline = pipelineY))
    assert(resultFail.isLeft)
  }

  test("SecretScope enum values") {
    assertEquals(SecretScope.values.length, 4)
    assert(SecretScope.values.contains(SecretScope.Job))
    assert(SecretScope.values.contains(SecretScope.Workflow))
    assert(SecretScope.values.contains(SecretScope.Pipeline))
    assert(SecretScope.values.contains(SecretScope.Global))
  }
