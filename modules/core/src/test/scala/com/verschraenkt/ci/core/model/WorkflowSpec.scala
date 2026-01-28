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
package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector
import munit.FunSuite

import scala.concurrent.duration.*

class WorkflowSpec extends FunSuite:

  val dummyStep: Step  = Step.Checkout()(using StepMeta())
  val dummyStep2: Step = Step.Run(Command.Exec("echo", List("test")))(using StepMeta())
  val dummyStep3: Step = Step.Run(Command.Shell("ls -la"))(using StepMeta())

  val job1: Job = Job.one(JobId("job1"), dummyStep)
  val job2: Job = Job.one(JobId("job2"), dummyStep2)
  val job3: Job = Job.one(JobId("job3"), dummyStep3)

  test("Workflow creation with all parameters") {
    val container = Some(Container("node:18"))
    val workflow = Workflow(
      name = "test-workflow",
      jobs = NonEmptyVector.of(job1, job2),
      defaultContainer = container,
      concurrencyGroup = Some("ci-group"),
      labels = Set("linux", "docker")
    )

    assertEquals(workflow.name, "test-workflow")
    assertEquals(workflow.jobs.length, 2)
    assertEquals(workflow.defaultContainer, container)
    assertEquals(workflow.concurrencyGroup, Some("ci-group"))
    assertEquals(workflow.labels, Set("linux", "docker"))
  }

  test("Workflow creation with default values") {
    val workflow = Workflow(
      name = "simple-workflow",
      jobs = NonEmptyVector.one(job1),
      defaultContainer = None
    )

    assertEquals(workflow.name, "simple-workflow")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.defaultContainer, None)
    assertEquals(workflow.concurrencyGroup, None)
    assertEquals(workflow.labels, Set.empty)
  }

  test("Workflow.one creates workflow with single job") {
    val container = Some(Container("alpine:latest"))
    val workflow = Workflow.one(
      name = "single-job-workflow",
      job = job1,
      defaultContainer = container,
      concurrencyGroup = Some("deployment"),
      labels = Set("production")
    )

    assertEquals(workflow.name, "single-job-workflow")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.jobs.head, job1)
    assertEquals(workflow.defaultContainer, container)
    assertEquals(workflow.concurrencyGroup, Some("deployment"))
    assertEquals(workflow.labels, Set("production"))
  }

  test("Workflow.one with minimal parameters") {
    val workflow = Workflow.one(
      name = "minimal-workflow",
      job = job1
    )

    assertEquals(workflow.name, "minimal-workflow")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.defaultContainer, None)
    assertEquals(workflow.concurrencyGroup, None)
    assertEquals(workflow.labels, Set.empty)
  }

  test("Workflow.of creates workflow with multiple jobs") {
    val workflow = Workflow.of(
      name = "multi-job-workflow",
      first = job1,
      rest = job2,
      job3
    )

    assertEquals(workflow.name, "multi-job-workflow")
    assertEquals(workflow.jobs.length, 3)
    assertEquals(workflow.jobs.toVector, Vector(job1, job2, job3))
  }

  test("Workflow.of with single job") {
    val workflow = Workflow.of(
      name = "single-from-of",
      first = job1
    )

    assertEquals(workflow.name, "single-from-of")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.jobs.head, job1)
  }

  test("add method adds single job to workflow") {
    val workflow = Workflow.one("workflow", job1)
    val extended = workflow.addJob(job2)

    assertEquals(extended.jobs.length, 2)
    assertEquals(extended.jobs.toVector, Vector(job1, job2))
    assertEquals(extended.name, workflow.name)
  }

  test("add method chains multiple jobs") {
    val workflow = Workflow.one("workflow", job1)
    val result   = workflow.addJob(job2).addJob(job3)

    assertEquals(result.jobs.length, 3)
    assertEquals(result.jobs.toVector, Vector(job1, job2, job3))
  }

  test("++ operator adds multiple jobs to workflow") {
    val workflow = Workflow.one("workflow", job1)
    val moreJobs = NonEmptyVector.of(job2, job3)
    val extended = workflow.addJobs(moreJobs)

    assertEquals(extended.jobs.length, 3)
    assertEquals(extended.jobs.toVector, Vector(job1, job2, job3))
    assertEquals(extended.name, workflow.name)
  }

  test("++ operator preserves workflow properties") {
    val container = Some(Container("ubuntu:22.04"))
    val workflow = Workflow.one(
      name = "preserve-test",
      job = job1,
      defaultContainer = container,
      concurrencyGroup = Some("ci"),
      labels = Set("test")
    )
    val moreJobs = NonEmptyVector.of(job2, job3)
    val extended = workflow.addJobs(moreJobs)

    assertEquals(extended.name, workflow.name)
    assertEquals(extended.defaultContainer, container)
    assertEquals(extended.concurrencyGroup, Some("ci"))
    assertEquals(extended.labels, Set("test"))
    assertEquals(extended.jobs.length, 3)
  }

  test("Workflow.materialize applies default container to jobs without container") {
    val defaultContainer    = Some(Container("node:16"))
    val jobWithContainer    = job1.copy(container = Some(Container("python:3.9")))
    val jobWithoutContainer = job2.copy(container = None)

    val workflow = Workflow(
      name = "materialize-test",
      jobs = NonEmptyVector.of(jobWithContainer, jobWithoutContainer),
      defaultContainer = defaultContainer
    )

    val materialized = Workflow.materialize(workflow)

    assertEquals(materialized.length, 2)
    assertEquals(materialized.head.container, Some(Container("python:3.9")))
    assertEquals(materialized.tail.head.container, defaultContainer)
  }

  test("Workflow.materialize merges workflow labels with job labels") {
    val jobWithLabels    = job1.copy(labels = Set("fast"))
    val jobWithoutLabels = job2.copy(labels = Set.empty)

    val workflow = Workflow(
      name = "labels-test",
      jobs = NonEmptyVector.of(jobWithLabels, jobWithoutLabels),
      defaultContainer = None,
      labels = Set("linux", "ci")
    )

    val materialized = Workflow.materialize(workflow)

    assertEquals(materialized.head.labels, Set("fast", "linux", "ci"))
    assertEquals(materialized.tail.head.labels, Set("linux", "ci"))
  }

  test("Workflow.materialize preserves job properties") {
    val workflow     = Workflow.one("test", job1, labels = Set("workflow-label"))
    val materialized = Workflow.materialize(workflow)

    val original = workflow.jobs.head
    val result   = materialized.head

    assertEquals(result.id, original.id)
    assertEquals(result.steps, original.steps)
    assertEquals(result.dependencies, original.dependencies)
    assertEquals(result.resources, original.resources)
    assertEquals(result.timeout, original.timeout)
    assertEquals(result.matrix, original.matrix)
    assertEquals(result.concurrencyGroup, original.concurrencyGroup)
  }

  test("materialized extension method applies materialization") {
    val defaultContainer = Some(Container("alpine:latest"))
    val workflow = Workflow.one(
      "workflow",
      job1.copy(container = None),
      defaultContainer = defaultContainer,
      labels = Set("production")
    )

    val materialized = workflow.materialized

    assertEquals(materialized.length, 1)
    assertEquals(materialized.head.container, defaultContainer)
    assertEquals(materialized.head.labels, Set("production"))
  }

  test("complex workflow with multiple jobs and dependencies") {
    val buildJob  = Job.one(JobId("build"), dummyStep)
    val testJob   = Job.one(JobId("test"), dummyStep2).needs(JobId("build"))
    val deployJob = Job.one(JobId("deploy"), dummyStep3).needs(JobId("test"))

    val workflow = Workflow.of(
      name = "ci-cd-pipeline",
      first = buildJob,
      rest = testJob,
      deployJob
    )

    assertEquals(workflow.jobs.length, 3)
    assertEquals(workflow.jobs.toVector(0).id, JobId("build"))
    assertEquals(workflow.jobs.toVector(1).dependencies, Set(JobId("build")))
    assertEquals(workflow.jobs.toVector(2).dependencies, Set(JobId("test")))
  }

  test("workflow with matrix jobs") {
    val matrixJob = Job.one(
      JobId("matrix-build"),
      dummyStep,
      matrix = Map(
        "os"      -> NonEmptyVector.of("linux", "windows", "macos"),
        "version" -> NonEmptyVector.of("14", "16", "18")
      )
    )

    val workflow = Workflow.one("matrix-workflow", matrixJob)

    assertEquals(workflow.jobs.head.matrix.size, 2)
    assertEquals(workflow.jobs.head.matrix("os").length, 3)
    assertEquals(workflow.jobs.head.matrix("version").length, 3)
  }

  test("workflow name can contain special characters") {
    val workflow = Workflow.one("CI/CD Pipeline - Build & Test", job1)
    assertEquals(workflow.name, "CI/CD Pipeline - Build & Test")
  }

  test("workflow with concurrency group") {
    val workflow = Workflow.one(
      name = "concurrent-workflow",
      job = job1,
      concurrencyGroup = Some("deployment-prod")
    )

    assertEquals(workflow.concurrencyGroup, Some("deployment-prod"))
  }

  test("workflow labels can be empty") {
    val workflow = Workflow.one("no-labels", job1, labels = Set.empty)
    assertEquals(workflow.labels, Set.empty)
  }

  test("workflow labels can contain multiple values") {
    val labels   = Set("linux", "docker", "fast", "production")
    val workflow = Workflow.one("labeled", job1, labels = labels)
    assertEquals(workflow.labels, labels)
  }

  test("add method preserves all workflow properties") {
    val container = Some(Container("node:18"))
    val workflow = Workflow.one(
      name = "preserve-all",
      job = job1,
      defaultContainer = container,
      concurrencyGroup = Some("ci"),
      labels = Set("test", "linux")
    )
    val extended = workflow.addJob(job2)

    assertEquals(extended.name, workflow.name)
    assertEquals(extended.defaultContainer, container)
    assertEquals(extended.concurrencyGroup, Some("ci"))
    assertEquals(extended.labels, Set("test", "linux"))
  }

  test("materialize handles workflow without default container") {
    val jobWithContainer = job1.copy(container = Some(Container("alpine")))
    val workflow         = Workflow.one("no-default", jobWithContainer, defaultContainer = None)

    val materialized = Workflow.materialize(workflow)
    assertEquals(materialized.head.container, Some(Container("alpine")))
  }

  test("materialize handles workflow without labels") {
    val workflow     = Workflow.one("no-labels", job1, labels = Set.empty)
    val materialized = Workflow.materialize(workflow)

    assertEquals(materialized.head.labels, Set.empty)
  }

  test("workflow with all jobs having containers") {
    val job1WithContainer = job1.copy(container = Some(Container("node:18")))
    val job2WithContainer = job2.copy(container = Some(Container("python:3.9")))

    val workflow = Workflow(
      name = "all-containerized",
      jobs = NonEmptyVector.of(job1WithContainer, job2WithContainer),
      defaultContainer = Some(Container("alpine"))
    )

    val materialized = Workflow.materialize(workflow)
    assertEquals(materialized.head.container, Some(Container("node:18")))
    assertEquals(materialized.tail.head.container, Some(Container("python:3.9")))
  }

  test("workflow with multiple label sets merged correctly") {
    val job1WithLabels = job1.copy(labels = Set("fast", "unit-test"))
    val job2WithLabels = job2.copy(labels = Set("integration", "slow"))

    val workflow = Workflow(
      name = "label-merge",
      jobs = NonEmptyVector.of(job1WithLabels, job2WithLabels),
      defaultContainer = None,
      labels = Set("ci", "automated")
    )

    val materialized = Workflow.materialize(workflow)
    assertEquals(materialized.head.labels, Set("fast", "unit-test", "ci", "automated"))
    assertEquals(materialized.tail.head.labels, Set("integration", "slow", "ci", "automated"))
  }

  test("build complete workflow using fluent API") {
    val checkout = Step.Checkout()(using StepMeta())
    val build    = Step.Run(Command.Exec("npm", List("run", "build")))(using StepMeta())
    val test     = Step.Run(Command.Exec("npm", List("test")))(using StepMeta())

    val buildJob = Job.one(JobId("build"), checkout).~>(build)
    val testJob  = Job.one(JobId("test"), test).needs(JobId("build"))

    val workflow = Workflow
      .one("ci-pipeline", buildJob)
      .addJob(testJob)

    assertEquals(workflow.jobs.length, 2)
    assertEquals(workflow.jobs.head.steps.length, 2)
    assertEquals(workflow.jobs.tail.head.dependencies, Set(JobId("build")))
  }

  test("empty labels set does not affect materialization") {
    val workflow     = Workflow.one("test", job1)
    val materialized = workflow.materialized

    assertEquals(materialized.head.labels, job1.labels)
  }

  test("workflow can be built incrementally") {
    val workflow = Workflow
      .one("incremental", job1)
      .addJob(job2)
      .addJob(job3)

    assertEquals(workflow.jobs.length, 3)
    assertEquals(workflow.jobs.toVector, Vector(job1, job2, job3))
  }

  test("materialized creates new NonEmptyVector") {
    val workflow      = Workflow.one("test", job1, labels = Set("new-label"))
    val materialized1 = workflow.materialized
    val materialized2 = workflow.materialized

    assertEquals(materialized1, materialized2)
    assertEquals(materialized1.head.labels, materialized2.head.labels)
  }

  test("workflow with single job and multiple steps") {
    val multiStepJob = Job.of(
      JobId("multi-step"),
      dummyStep,
      dummyStep2,
      dummyStep3
    )()

    val workflow = Workflow.one("workflow", multiStepJob)

    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.jobs.head.steps.length, 3)
  }

  // Tests for Issue 2.4: concurrencyGroup propagation in materialize
  test("Workflow.materialize propagates concurrencyGroup to jobs without one") {
    val jobWithConcurrency = job1.copy(concurrencyGroup = Some("job-group"))
    val jobWithoutConcurrency = job2.copy(concurrencyGroup = None)

    val workflow = Workflow(
      name = "concurrency-test",
      jobs = NonEmptyVector.of(jobWithConcurrency, jobWithoutConcurrency),
      defaultContainer = None,
      concurrencyGroup = Some("workflow-group")
    )

    val materialized = Workflow.materialize(workflow)

    // Job with its own concurrencyGroup should keep it
    assertEquals(materialized.head.concurrencyGroup, Some("job-group"))
    // Job without concurrencyGroup should inherit from workflow
    assertEquals(materialized.tail.head.concurrencyGroup, Some("workflow-group"))
  }

  test("Workflow.materialize does not override job concurrencyGroup") {
    val jobWithConcurrency = job1.copy(concurrencyGroup = Some("my-group"))

    val workflow = Workflow.one(
      name = "override-test",
      job = jobWithConcurrency,
      concurrencyGroup = Some("workflow-group")
    )

    val materialized = Workflow.materialize(workflow)
    assertEquals(materialized.head.concurrencyGroup, Some("my-group"))
  }

  test("Workflow.materialize leaves job concurrencyGroup as None when workflow has none") {
    val jobWithoutConcurrency = job1.copy(concurrencyGroup = None)

    val workflow = Workflow.one(
      name = "no-concurrency",
      job = jobWithoutConcurrency,
      concurrencyGroup = None
    )

    val materialized = Workflow.materialize(workflow)
    assertEquals(materialized.head.concurrencyGroup, None)
  }

  test("materialized extension method propagates concurrencyGroup") {
    val workflow = Workflow.one(
      "extension-concurrency",
      job1.copy(concurrencyGroup = None),
      concurrencyGroup = Some("workflow-conc")
    )

    val materialized = workflow.materialized
    assertEquals(materialized.head.concurrencyGroup, Some("workflow-conc"))
  }
