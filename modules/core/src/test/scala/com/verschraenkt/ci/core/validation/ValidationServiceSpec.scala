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
package com.verschraenkt.ci.core.validation

import cats.data.{ NonEmptyList, NonEmptyVector }
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.errors.{ CompositeError, ValidationError }
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*

class ValidationServiceSpec extends FunSuite:

  val validStep: Step           = Step.Checkout()(using StepMeta())
  val validContainer: Container = Container("ubuntu:22.04")

  given ctx: ApplicationContext = ApplicationContext("test-source")

  // Pipeline Tests
  test("validatePipeline - valid pipeline passes") {
    val workflow = Workflow.one("test-workflow", Job.one(JobId("job1"), validStep))
    val pipeline = Pipeline.one(PipelineId("test-pipeline"), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Right(p) => assertEquals(p.id, PipelineId("test-pipeline"))
      case Left(e)  => fail(s"Expected valid pipeline but got: $e")
  }

  test("validatePipeline - empty ID fails") {
    val workflow = Workflow.one("test-workflow", Job.one(JobId("job1"), validStep))
    val pipeline = Pipeline.one(PipelineId(""), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Left(e)  => assert(e.errors.exists(_.message.contains("cannot be empty")))
      case Right(_) => fail("Expected validation error for empty pipeline ID")
  }

  test("validatePipeline - invalid characters in ID fails") {
    val workflow = Workflow.one("test-workflow", Job.one(JobId("job1"), validStep))
    val pipeline = Pipeline.one(PipelineId("invalid pipeline!"), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Left(e)  => assert(e.errors.exists(_.message.contains("alphanumeric")))
      case Right(_) => fail("Expected validation error for invalid pipeline ID")
  }

  test("validatePipeline - ID too long fails") {
    val workflow = Workflow.one("test-workflow", Job.one(JobId("job1"), validStep))
    val longId   = "a" * 256
    val pipeline = Pipeline.one(PipelineId(longId), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Left(e)  => assert(e.errors.exists(_.message.contains("255 characters")))
      case Right(_) => fail("Expected validation error for long pipeline ID")
  }

  test("validatePipeline - timeout too long fails") {
    val workflow = Workflow.one("test-workflow", Job.one(JobId("job1"), validStep))
    val pipeline = Pipeline.one(PipelineId("test"), workflow, timeout = Some(25.hours))

    ValidationService.validatePipeline(pipeline) match
      case Left(e)  => assert(e.errors.exists(_.message.contains("24 hours")))
      case Right(_) => fail("Expected validation error for excessive timeout")
  }

  // Workflow Tests
  test("validateWorkflow - valid workflow passes") {
    val job      = Job.one(JobId("job1"), validStep)
    val workflow = Workflow.one("test-workflow", job)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Valid(w)   => assertEquals(w.name, "test-workflow")
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid workflow but got: $e")
  }

  test("validateWorkflow - empty name fails") {
    val job      = Job.one(JobId("job1"), validStep)
    val workflow = Workflow.one("", job)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty workflow name")
  }

  test("validateWorkflow - name too long fails") {
    val job      = Job.one(JobId("job1"), validStep)
    val longName = "a" * 256
    val workflow = Workflow.one(longName, job)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("255 characters")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for long workflow name")
  }

  // Job Tests
  test("validateJob - valid job passes") {
    val job = Job.one(JobId("valid-job"), validStep)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Valid(j)   => assertEquals(j.id, JobId("valid-job"))
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid job but got: $e")
  }

  test("validateJob - empty ID fails") {
    val job = Job.one(JobId(""), validStep)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty job ID")
  }

  test("validateJob - invalid characters in ID fails") {
    val job = Job.one(JobId("invalid job!"), validStep)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("alphanumeric")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for invalid job ID")
  }

  test("validateJob - self-dependency fails") {
    val jobId = JobId("self-dep")
    val job   = Job.one(jobId, validStep, needs = Set(jobId))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("cannot depend on itself")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for self-dependency")
  }

  test("validateJob - zero CPU fails") {
    val job = Job.one(JobId("job1"), validStep, resources = Resource(0, 512))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("CPU must be positive")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for zero CPU")
  }

  test("validateJob - negative memory fails") {
    val job = Job.one(JobId("job1"), validStep, resources = Resource.unsafe(1000, -512, 0, 0))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Memory must be positive")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for negative memory")
  }

  test("validateJob - negative GPU fails") {
    val job = Job.one(JobId("job1"), validStep, resources = Resource.unsafe(1000, 512, -1, 0))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("GPU count cannot be negative")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for negative GPU")
  }

  test("validateJob - zero timeout fails") {
    val job = Job.one(JobId("job1"), validStep, timeout = 0.seconds)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("must be positive")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for zero timeout")
  }

  test("validateJob - excessive timeout fails") {
    val job = Job.one(JobId("job1"), validStep, timeout = 25.hours)

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("24 hours")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for excessive timeout")
  }

  // Job Dependency Tests
  test("validateJobs - circular dependency fails") {
    val job1     = Job.one(JobId("job1"), validStep, needs = Set(JobId("job2")))
    val job2     = Job.one(JobId("job2"), validStep, needs = Set(JobId("job1")))
    val workflow = Workflow.of("test", job1, job2)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("Cyclic dependency")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for circular dependency")
  }

  test("validateJobs - missing dependency fails") {
    val job1     = Job.one(JobId("job1"), validStep, needs = Set(JobId("nonexistent")))
    val workflow = Workflow.one("test", job1)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Unknown dependencies")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for missing dependency")
  }

  test("validateJobs - valid dependencies pass") {
    val job1     = Job.one(JobId("job1"), validStep)
    val job2     = Job.one(JobId("job2"), validStep, needs = Set(JobId("job1")))
    val workflow = Workflow.of("test", job1, job2)

    ValidationService.validateWorkflow(workflow) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid dependencies but got: $e")
  }

  // Matrix Tests
  test("validateJob - empty matrix key fails") {
    val job = Job.one(
      JobId("job1"),
      validStep,
      matrix = Map("" -> NonEmptyVector.of("a", "b"))
    )

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Matrix key cannot be empty")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for empty matrix key")
  }

  test("validateJob - too many matrix values fails") {
    val values = (1 to 257).map(_.toString).toVector
    val job = Job.one(
      JobId("job1"),
      validStep,
      matrix = Map("key" -> NonEmptyVector.fromVectorUnsafe(values))
    )

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("too many values")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for too many matrix values")
  }

  test("validateJob - too many matrix combinations fails") {
    val job = Job.one(
      JobId("job1"),
      validStep,
      matrix = Map(
        "a" -> NonEmptyVector.fromVectorUnsafe((1 to 20).map(_.toString).toVector),
        "b" -> NonEmptyVector.fromVectorUnsafe((1 to 20).map(_.toString).toVector)
      )
    )

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("too many combinations")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for too many matrix combinations")
  }

  test("validateJob - matrix with empty value fails") {
    val job = Job.one(
      JobId("job1"),
      validStep,
      matrix = Map("os" -> NonEmptyVector.of("linux", "", "macos"))
    )

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("empty values")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty matrix value")
  }

  // Container Tests
  test("validateJob - empty container image fails") {
    val job = Job.one(JobId("job1"), validStep, container = Some(Container("")))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("Container image cannot be empty")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for empty container image")
  }

  test("validateJob - container image too long fails") {
    val longImage = "a" * 513
    val job       = Job.one(JobId("job1"), validStep, container = Some(Container(longImage)))

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("512 characters")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for long container image")
  }

  // Step Tests
  test("validateStep - valid checkout passes") {
    val step = Step.Checkout()(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid checkout step but got: $e")
  }

  test("validateStep - valid run with exec passes") {
    val step = Step.Run(Command.Exec("echo", List("hello")))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid run step but got: $e")
  }

  test("validateStep - empty exec program fails") {
    val step = Step.Run(Command.Exec("", List("hello")))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty exec program")
  }

  test("validateStep - empty shell script fails") {
    val step = Step.Run(Command.Shell(""))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty shell script")
  }

  test("validateStep - negative command timeout fails") {
    val step = Step.Run(Command.Exec("echo", List("test"), timeoutSec = Some(-1)))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("must be positive")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for negative timeout")
  }

  test("validateStep - excessive command timeout fails") {
    val step = Step.Run(Command.Exec("echo", List("test"), timeoutSec = Some(86401)))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("24 hours")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for excessive timeout")
  }

  // Step Meta Tests
  test("validateStep - invalid retry max attempts fails") {
    val meta = StepMeta(retry = Some(Retry(0, 1.second)))
    val step = Step.Checkout()(using meta)

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("at least 1")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for invalid retry attempts")
  }

  test("validateStep - too many retry attempts fails") {
    val meta = StepMeta(retry = Some(Retry(11, 1.second)))
    val step = Step.Checkout()(using meta)

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot exceed 10")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for too many retry attempts")
  }

  test("validateStep - negative retry delay fails") {
    val meta = StepMeta(retry = Some(Retry(3, 0.seconds)))
    val step = Step.Checkout()(using meta)

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) =>
        assert(e.toList.exists(_.message.contains("delay must be positive")))
      case cats.data.Validated.Valid(_) => fail("Expected validation error for negative retry delay")
  }

  test("validateStep - valid retry configuration passes") {
    val meta = StepMeta(retry = Some(Retry(3, 5.seconds)))
    val step = Step.Checkout()(using meta)

    ValidationService.validateStep(step) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid retry configuration but got: $e")
  }

  // Cache Tests
  test("validateStep - valid restore cache passes") {
    val cache = Cache.RestoreCache(CacheKey.literal("build-cache"), NonEmptyList.of("/build"))
    val step  = Step.RestoreCache(cache, NonEmptyList.of("/build"))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid restore cache but got: $e")
  }

  test("validateStep - valid save cache passes") {
    val cache = Cache.SaveCache(CacheKey.literal("build-cache"), NonEmptyList.of("/build"))
    val step  = Step.SaveCache(cache, NonEmptyList.of("/build"))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid save cache but got: $e")
  }

  test("validateStep - empty cache path fails") {
    val cache = Cache.RestoreCache(CacheKey.literal("build-cache"), NonEmptyList.of(""))
    val step  = Step.RestoreCache(cache, NonEmptyList.of(""))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for empty cache path")
  }

  test("validateStep - cache path too long fails") {
    val longPath = "a" * 1025
    val cache    = Cache.RestoreCache(CacheKey.literal("build-cache"), NonEmptyList.of(longPath))
    val step     = Step.RestoreCache(cache, NonEmptyList.of(longPath))(using StepMeta())

    ValidationService.validateStep(step) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("1024 characters")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for long cache path")
  }

  // Composite Step Tests
  test("validateStep - valid composite step passes") {
    val step1     = Step.Checkout()(using StepMeta())
    val step2     = Step.Run(Command.Exec("echo", List("test")))(using StepMeta())
    val composite = Step.Composite(NonEmptyVector.of(step1, step2))

    ValidationService.validateStep(composite) match
      case cats.data.Validated.Valid(_)   => assert(true)
      case cats.data.Validated.Invalid(e) => fail(s"Expected valid composite step but got: $e")
  }

  test("validateStep - composite with invalid step fails") {
    val step1     = Step.Checkout()(using StepMeta())
    val step2     = Step.Run(Command.Exec("", List("test")))(using StepMeta())
    val composite = Step.Composite(NonEmptyVector.of(step1, step2))

    ValidationService.validateStep(composite) match
      case cats.data.Validated.Invalid(e) => assert(e.toList.exists(_.message.contains("cannot be empty")))
      case cats.data.Validated.Valid(_)   => fail("Expected validation error for invalid composite step")
  }

  // Multiple Errors Test
  test("validateJob - accumulates multiple errors") {
    val job = Job.one(
      JobId(""),
      validStep,
      resources = Resource.unsafe(0, 0, -1, -100),
      timeout = 0.seconds
    )

    ValidationService.validateJob(job) match
      case cats.data.Validated.Invalid(errors) =>
        assert(errors.length >= 5)
        assert(errors.toList.exists(_.message.contains("Job ID cannot be empty")))
        assert(errors.toList.exists(_.message.contains("CPU must be positive")))
        assert(errors.toList.exists(_.message.contains("Memory must be positive")))
        assert(errors.toList.exists(_.message.contains("GPU count cannot be negative")))
      case cats.data.Validated.Valid(_) => fail("Expected multiple validation errors")
  }

  // Integration Tests
  test("validatePipeline - complex valid pipeline passes") {
    val step1 = Step.Checkout()(using StepMeta())
    val step2 = Step.Run(Command.Exec("npm", List("install")))(using StepMeta())
    val step3 = Step.Run(Command.Shell("npm test"))(using StepMeta())

    val job1 = Job
      .one(JobId("build"), step1)
      .~>(step2)
      .~>(step3)

    val job2 = Job
      .one(JobId("deploy"), Step.Run(Command.Exec("deploy", List("prod")))(using StepMeta()))
      .needs(JobId("build"))

    val workflow = Workflow.of("ci-cd", job1, job2)
    val pipeline = Pipeline.one(PipelineId("main-pipeline"), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Right(p) =>
        assertEquals(p.id, PipelineId("main-pipeline"))
        assertEquals(p.workflows.length, 1)
      case Left(e) => fail(s"Expected valid complex pipeline but got: $e")
  }

  test("validatePipeline - complex invalid pipeline accumulates all errors") {
    val invalidStep = Step.Run(Command.Exec(""))(using StepMeta())
    val invalidJob = Job.one(
      JobId(""),
      invalidStep,
      resources = Resource.unsafe(0, 0, 0, 0),
      timeout = 0.seconds,
      container = Some(Container(""))
    )

    val workflow = Workflow.one("", invalidJob)
    val pipeline = Pipeline.one(PipelineId(""), workflow)

    ValidationService.validatePipeline(pipeline) match
      case Left(e) =>
        assert(e.errors.length >= 5)
        assert(e.errors.exists(_.message.contains("Pipeline ID cannot be empty")))
        assert(e.errors.exists(_.message.contains("Workflow name cannot be empty")))
        assert(e.errors.exists(_.message.contains("Job ID cannot be empty")))
      case Right(_) => fail("Expected multiple validation errors in complex pipeline")
  }
