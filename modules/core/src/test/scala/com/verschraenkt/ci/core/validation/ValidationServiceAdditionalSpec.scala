/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * See License
 */
package com.verschraenkt.ci.core.validation

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*

class ValidationServiceAdditionalSpec extends FunSuite:

  val validStep: Step = Step.Checkout()(using StepMeta())

  given ctx: ApplicationContext = ApplicationContext("test-additional")

  // Duplicate Step ID Tests
  test("validateJob - duplicate step IDs fails") {
    val step1 = Step.Checkout()(using StepMeta(id = Some("checkout")))
    val step2 = Step.Run(Command.Shell("echo hello"))(using StepMeta(id = Some("checkout")))

    val job = Job.of(JobId("job1"), step1, step2)()

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Duplicate step ID: checkout")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for duplicate step IDs")
  }

  test("validateJob - duplicate step IDs in composite step fails") {
    val step1     = Step.Checkout()(using StepMeta(id = Some("step1")))
    val step2     = Step.Run(Command.Shell("echo hello"))(using StepMeta(id = Some("step1")))
    val composite = Step.Composite(NonEmptyVector.of(step1, step2))

    val job = Job.one(JobId("job1"), composite)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Duplicate step ID: step1")))
      case cats.data.Validated.Valid(_) =>
        fail("Expected validation error for duplicate step IDs in composite step")
  }

  // Duplicate Workflow Name Tests
  test("validatePipeline - duplicate workflow names fails") {
    val job = Job.one(JobId("job1"), validStep)
    val wf1 = Workflow.one("setup", job)
    val wf2 = Workflow.one("setup", job) // Duplicate name

    val pipeline = Pipeline.of(PipelineId("pipe1"), wf1, wf2)

    ValidationService.validatePipeline(pipeline) match
      case Left(e) =>
        assert(e.errors.exists(_.message.contains("Duplicate workflow name: setup")))
      case Right(_) => fail("Expected validation error for duplicate workflow names")
  }

  // Label Validation Tests
  test("validatePipeline - invalid label fails") {
    val job      = Job.one(JobId("job1"), validStep)
    val workflow = Workflow.one("wf1", job)
    val pipeline = Pipeline.one(PipelineId("pipe1"), workflow, labels = Set("invalid label!"))

    ValidationService.validatePipeline(pipeline) match
      case Left(e) =>
        assert(
          e.errors.exists(
            _.message.contains("must contain only alphanumeric characters, underscores, and hyphens")
          )
        )
      case Right(_) => fail("Expected validation error for invalid pipeline label")
  }

  test("validateJob - invalid label fails") {
    val job = Job.one(JobId("job1"), validStep, labels = Set("bad$label"))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(
          e.toList.exists(
            _.message.contains("must contain only alphanumeric characters, underscores, and hyphens")
          )
        )
      case cats.data.Validated.Valid(_) => fail("Expected validation error for invalid job label")
  }

  test("validateWorkflow - invalid label fails") {
    val job      = Job.one(JobId("job1"), validStep)
    val workflow = Workflow.one("wf1", job, labels = Set("wrong@label"))

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Invalid(e) =>
        assert(
          e.toList.exists(
            _.message.contains("must contain only alphanumeric characters, underscores, and hyphens")
          )
        )
      case cats.data.Validated.Valid(_) => fail("Expected validation error for invalid workflow label")
  }
