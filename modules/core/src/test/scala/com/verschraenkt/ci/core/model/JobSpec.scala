package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector
import munit.FunSuite

import scala.concurrent.duration.*

class JobSpec extends FunSuite:

  val dummyStep: Step  = Step.Checkout()(using StepMeta())
  val dummyStep2: Step = Step.Run(Command.Exec("echo", List("test")))(using StepMeta())
  val dummyStep3: Step = Step.Run(Command.Shell("ls -la"))(using StepMeta())

  test("Job creation with all parameters") {
    val job = Job(
      id = JobId("test-job"),
      steps = NonEmptyVector.one(dummyStep),
      dependencies = Set(JobId("dep1"), JobId("dep2")),
      resources = Resource(2000, 1024, 1, 500),
      timeout = 45.minutes,
      matrix = Map("os" -> NonEmptyVector.of("linux", "windows"), "node" -> NonEmptyVector.of("14", "16")),
      container = Some(Container("node:16-alpine")),
      labels = Set("ubuntu", "fast"),
      concurrencyGroup = Some("deployment")
    )

    assertEquals(job.id, JobId("test-job"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.dependencies, Set(JobId("dep1"), JobId("dep2")))
    assertEquals(job.resources, Resource(2000, 1024, 1, 500))
    assertEquals(job.timeout, 45.minutes)
    assertEquals(job.matrix.size, 2)
    assertEquals(job.container.map(_.image), Some("node:16-alpine"))
    assertEquals(job.labels, Set("ubuntu", "fast"))
    assertEquals(job.concurrencyGroup, Some("deployment"))
  }

  test("Job creation with default values") {
    val job = Job(
      id = JobId("simple-job"),
      steps = NonEmptyVector.one(dummyStep),
      timeout = 30.minutes,
      container = None
    )

    assertEquals(job.id, JobId("simple-job"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.dependencies, Set.empty)
    assertEquals(job.resources, Resource(1000, 512, 0, 0))
    assertEquals(job.timeout, 30.minutes)
    assertEquals(job.matrix, Map.empty)
    assertEquals(job.container, None)
    assertEquals(job.labels, Set.empty)
    assertEquals(job.concurrencyGroup, None)
  }

  test("Job.one creates job with single step") {
    val job = Job.one(
      id = JobId("one-step"),
      step = dummyStep
    )

    assertEquals(job.id, JobId("one-step"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.steps.head, dummyStep)
    assertEquals(job.timeout, 30.minutes)
  }

  test("Job.one with custom parameters") {
    val job = Job.one(
      id = JobId("custom-job"),
      step = dummyStep,
      needs = Set(JobId("build")),
      resources = Resource(3000, 2048),
      timeout = 60.minutes,
      container = Some(Container("alpine:latest")),
      labels = Set("linux"),
      concurrencyGroup = Some("ci")
    )

    assertEquals(job.id, JobId("custom-job"))
    assertEquals(job.dependencies, Set(JobId("build")))
    assertEquals(job.resources, Resource(3000, 2048))
    assertEquals(job.timeout, 60.minutes)
    assertEquals(job.container.map(_.image), Some("alpine:latest"))
    assertEquals(job.labels, Set("linux"))
    assertEquals(job.concurrencyGroup, Some("ci"))
  }

  test("Job.of creates job with multiple steps") {
    val job = Job.of(
      id = JobId("multi-step"),
      first = dummyStep,
      rest = dummyStep2,
      dummyStep3
    )(
      timeout = 45.minutes,
      container = Some(Container("ubuntu:22.04"))
    )

    assertEquals(job.id, JobId("multi-step"))
    assertEquals(job.steps.length, 3)
    assertEquals(job.steps.toVector, Vector(dummyStep, dummyStep2, dummyStep3))
    assertEquals(job.timeout, 45.minutes)
    assertEquals(job.container.map(_.image), Some("ubuntu:22.04"))
  }

  test("Job.of with single step") {
    val job = Job.of(
      id = JobId("single-step"),
      first = dummyStep
    )()

    assertEquals(job.id, JobId("single-step"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.timeout, 30.minutes)
    assertEquals(job.container, None)
  }

  test("~> operator adds single step to job") {
    val job      = Job.one(JobId("job1"), dummyStep)
    val extended = job ~> dummyStep2

    assertEquals(extended.steps.length, 2)
    assertEquals(extended.steps.toVector, Vector(dummyStep, dummyStep2))
    assertEquals(extended.id, job.id)
  }

  test("~> operator adds multiple steps to job") {
    val job       = Job.one(JobId("job1"), dummyStep)
    val moreSteps = NonEmptyVector.of(dummyStep2, dummyStep3)
    val extended  = job ~> moreSteps

    assertEquals(extended.steps.length, 3)
    assertEquals(extended.steps.toVector, Vector(dummyStep, dummyStep2, dummyStep3))
    assertEquals(extended.id, job.id)
  }

  test("~> operator chains multiple single steps") {
    val job    = Job.one(JobId("chain"), dummyStep)
    val result = job ~> dummyStep2 ~> dummyStep3

    assertEquals(result.steps.length, 3)
    assertEquals(result.steps.toVector, Vector(dummyStep, dummyStep2, dummyStep3))
  }

  test("needs method adds job dependencies") {
    val job      = Job.one(JobId("dependent"), dummyStep)
    val withDeps = job.needs(JobId("dep1"), JobId("dep2"))

    assertEquals(withDeps.dependencies, Set(JobId("dep1"), JobId("dep2")))
    assertEquals(withDeps.id, job.id)
  }

  test("needs method preserves existing dependencies") {
    val job = Job.one(
      JobId("job1"),
      dummyStep,
      needs = Set(JobId("existing"))
    )
    val withMore = job.needs(JobId("new1"), JobId("new2"))

    assertEquals(withMore.dependencies, Set(JobId("existing"), JobId("new1"), JobId("new2")))
  }

  test("needs method with no arguments") {
    val job       = Job.one(JobId("job1"), dummyStep)
    val unchanged = job.needs()

    assertEquals(unchanged.dependencies, Set.empty)
  }

  test("~> operator preserves job properties") {
    val job = Job.one(
      id = JobId("preserve-test"),
      step = dummyStep,
      needs = Set(JobId("dep1")),
      resources = Resource(2000, 1024),
      timeout = 45.minutes,
      container = Some(Container("node:16")),
      labels = Set("linux"),
      concurrencyGroup = Some("group1")
    )
    val extended = job ~> dummyStep2

    assertEquals(extended.id, job.id)
    assertEquals(extended.dependencies, job.dependencies)
    assertEquals(extended.resources, job.resources)
    assertEquals(extended.timeout, job.timeout)
    assertEquals(extended.container, job.container)
    assertEquals(extended.labels, job.labels)
    assertEquals(extended.concurrencyGroup, job.concurrencyGroup)
    assertEquals(extended.steps.length, 2)
  }

  test("JobId wraps string value") {
    val jobId = JobId("my-job-123")
    assertEquals(jobId.value, "my-job-123")
  }

  test("JobId equality") {
    val id1 = JobId("test")
    val id2 = JobId("test")
    val id3 = JobId("other")

    assertEquals(id1, id2)
    assert(id1 != id3)
  }

  test("matrix with multiple dimensions") {
    val job = Job.one(
      JobId("matrix-job"),
      dummyStep,
      matrix = Map(
        "os"      -> NonEmptyVector.of("linux", "windows", "macos"),
        "version" -> NonEmptyVector.of("1.0", "2.0"),
        "arch"    -> NonEmptyVector.of("x64", "arm64")
      )
    )

    assertEquals(job.matrix.size, 3)
    assertEquals(job.matrix("os").length, 3)
    assertEquals(job.matrix("version").length, 2)
    assertEquals(job.matrix("arch").length, 2)
  }

  test("empty matrix by default") {
    val job = Job.one(JobId("no-matrix"), dummyStep)
    assert(job.matrix.isEmpty)
  }

  test("complex job pipeline") {
    val checkout = Step.Checkout()(using StepMeta())
    val install  = Step.Run(Command.Exec("npm", List("install")))(using StepMeta())
    val test     = Step.Run(Command.Exec("npm", List("test")))(using StepMeta())
    val build    = Step.Run(Command.Exec("npm", List("run", "build")))(using StepMeta())

    val job = Job
      .one(JobId("ci"), checkout)
      .~>(install)
      .~>(test)
      .~>(build)
      .needs(JobId("lint"))

    assertEquals(job.steps.length, 4)
    assertEquals(job.dependencies, Set(JobId("lint")))
  }

  test("timeout can be specified in different units") {
    val job1 = Job.one(JobId("j1"), dummyStep, timeout = 30.minutes)
    val job2 = Job.one(JobId("j2"), dummyStep, timeout = 1.hour)
    val job3 = Job.one(JobId("j3"), dummyStep, timeout = 90.seconds)

    assertEquals(job1.timeout, 30.minutes)
    assertEquals(job2.timeout, 1.hour)
    assertEquals(job3.timeout, 90.seconds)
  }

  test("container with custom configuration") {
    val container = Container(
      image = "node:18-alpine",
      args = List("--production"),
      env = Map("NODE_ENV" -> "production", "PORT" -> "3000"),
      user = Some("node"),
      workdir = Some("/app")
    )
    val job = Job.one(JobId("containerized"), dummyStep, container = Some(container))

    job.container match
      case Some(c) =>
        assertEquals(c.image, "node:18-alpine")
        assertEquals(c.args, List("--production"))
        assertEquals(c.env.size, 2)
        assertEquals(c.user, Some("node"))
        assertEquals(c.workdir, Some("/app"))
      case None => fail("Expected container to be present")
  }
