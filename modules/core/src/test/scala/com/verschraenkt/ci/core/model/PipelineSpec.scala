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

class PipelineSpec extends FunSuite:

  val dummyStep: Step  = Step.Checkout()(using StepMeta())
  val dummyStep2: Step = Step.Run(Command.Exec("echo", List("test")))(using StepMeta())
  val dummyStep3: Step = Step.Run(Command.Shell("ls -la"))(using StepMeta())

  val job1: Job = Job.one(JobId("job1"), dummyStep)
  val job2: Job = Job.one(JobId("job2"), dummyStep2)
  val job3: Job = Job.one(JobId("job3"), dummyStep3)

  val workflow1: Workflow = Workflow.one("workflow1", job1)
  val workflow2: Workflow = Workflow.one("workflow2", job2)
  val workflow3: Workflow = Workflow.one("workflow3", job3)

  test("Pipeline creation with all parameters") {
    val pipeline = Pipeline(
      id = PipelineId("test-pipeline"),
      workflows = NonEmptyVector.of(workflow1, workflow2),
      concurrencyGroup = Some("ci-group"),
      labels = Set("production", "automated"),
      timeout = Some(2.hours)
    )

    assertEquals(pipeline.id, PipelineId("test-pipeline"))
    assertEquals(pipeline.workflows.length, 2)
    assertEquals(pipeline.concurrencyGroup, Some("ci-group"))
    assertEquals(pipeline.labels, Set("production", "automated"))
    assertEquals(pipeline.timeout, Some(2.hours))
  }

  test("Pipeline creation with default values") {
    val pipeline = Pipeline(
      id = PipelineId("simple-pipeline"),
      workflows = NonEmptyVector.one(workflow1)
    )

    assertEquals(pipeline.id, PipelineId("simple-pipeline"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.concurrencyGroup, None)
    assertEquals(pipeline.labels, Set.empty)
    assertEquals(pipeline.timeout, None)
  }

  test("PipelineId wraps string value") {
    val pipelineId = PipelineId("my-pipeline-123")
    assertEquals(pipelineId.value, "my-pipeline-123")
  }

  test("PipelineId equality") {
    val id1 = PipelineId("test")
    val id2 = PipelineId("test")
    val id3 = PipelineId("other")

    assertEquals(id1, id2)
    assert(id1 != id3)
  }

  test("Pipeline.one creates pipeline with single workflow") {
    val pipeline = Pipeline.one(
      id = PipelineId("single-workflow"),
      w = workflow1,
      concurrencyGroup = Some("deployment"),
      labels = Set("linux"),
      timeout = Some(1.hour)
    )

    assertEquals(pipeline.id, PipelineId("single-workflow"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.workflows.head, workflow1)
    assertEquals(pipeline.concurrencyGroup, Some("deployment"))
    assertEquals(pipeline.labels, Set("linux"))
    assertEquals(pipeline.timeout, Some(1.hour))
  }

  test("Pipeline.one with minimal parameters") {
    val pipeline = Pipeline.one(
      id = PipelineId("minimal"),
      w = workflow1
    )

    assertEquals(pipeline.id, PipelineId("minimal"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.concurrencyGroup, None)
    assertEquals(pipeline.labels, Set.empty)
    assertEquals(pipeline.timeout, None)
  }

  test("Pipeline.of creates pipeline with multiple workflows") {
    val pipeline = Pipeline.of(
      id = PipelineId("multi-workflow"),
      first = workflow1,
      rest = workflow2,
      workflow3
    )

    assertEquals(pipeline.id, PipelineId("multi-workflow"))
    assertEquals(pipeline.workflows.length, 3)
    assertEquals(pipeline.workflows.toVector, Vector(workflow1, workflow2, workflow3))
  }

  test("Pipeline.of with single workflow") {
    val pipeline = Pipeline.of(
      id = PipelineId("single-from-of"),
      first = workflow1
    )

    assertEquals(pipeline.id, PipelineId("single-from-of"))
    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.workflows.head, workflow1)
  }

  test("add method adds single workflow to pipeline") {
    val pipeline = Pipeline.one(PipelineId("pipeline"), workflow1)
    val extended = pipeline.add(workflow2)

    assertEquals(extended.workflows.length, 2)
    assertEquals(extended.workflows.toVector, Vector(workflow1, workflow2))
    assertEquals(extended.id, pipeline.id)
  }

  test("add method chains multiple workflows") {
    val pipeline = Pipeline.one(PipelineId("pipeline"), workflow1)
    val result   = pipeline.add(workflow2).add(workflow3)

    assertEquals(result.workflows.length, 3)
    assertEquals(result.workflows.toVector, Vector(workflow1, workflow2, workflow3))
  }

  test("++ operator adds multiple workflows to pipeline") {
    val pipeline      = Pipeline.one(PipelineId("pipeline"), workflow1)
    val moreWorkflows = NonEmptyVector.of(workflow2, workflow3)
    val extended      = pipeline ++ moreWorkflows

    assertEquals(extended.workflows.length, 3)
    assertEquals(extended.workflows.toVector, Vector(workflow1, workflow2, workflow3))
    assertEquals(extended.id, pipeline.id)
  }

  test("++ operator preserves pipeline properties") {
    val pipeline = Pipeline.one(
      id = PipelineId("preserve-test"),
      w = workflow1,
      concurrencyGroup = Some("ci"),
      labels = Set("test", "automated"),
      timeout = Some(30.minutes)
    )
    val moreWorkflows = NonEmptyVector.of(workflow2, workflow3)
    val extended      = pipeline ++ moreWorkflows

    assertEquals(extended.id, pipeline.id)
    assertEquals(extended.concurrencyGroup, Some("ci"))
    assertEquals(extended.labels, Set("test", "automated"))
    assertEquals(extended.timeout, Some(30.minutes))
    assertEquals(extended.workflows.length, 3)
  }

  test("add method preserves all pipeline properties") {
    val pipeline = Pipeline.one(
      id = PipelineId("preserve-all"),
      w = workflow1,
      concurrencyGroup = Some("deployment"),
      labels = Set("production", "critical"),
      timeout = Some(4.hours)
    )
    val extended = pipeline.add(workflow2)

    assertEquals(extended.id, pipeline.id)
    assertEquals(extended.concurrencyGroup, Some("deployment"))
    assertEquals(extended.labels, Set("production", "critical"))
    assertEquals(extended.timeout, Some(4.hours))
  }

  test("pipeline with timeout in different units") {
    val pipeline1 = Pipeline.one(PipelineId("p1"), workflow1, timeout = Some(30.minutes))
    val pipeline2 = Pipeline.one(PipelineId("p2"), workflow1, timeout = Some(2.hours))
    val pipeline3 = Pipeline.one(PipelineId("p3"), workflow1, timeout = Some(7200.seconds))

    assertEquals(pipeline1.timeout, Some(30.minutes))
    assertEquals(pipeline2.timeout, Some(2.hours))
    assertEquals(pipeline3.timeout, Some(7200.seconds))
    assertEquals(pipeline2.timeout.get.toSeconds, pipeline3.timeout.get.toSeconds)
  }

  test("pipeline with empty labels") {
    val pipeline = Pipeline.one(PipelineId("no-labels"), workflow1, labels = Set.empty)
    assertEquals(pipeline.labels, Set.empty)
  }

  test("pipeline with multiple labels") {
    val labels   = Set("linux", "docker", "kubernetes", "production")
    val pipeline = Pipeline.one(PipelineId("labeled"), workflow1, labels = labels)
    assertEquals(pipeline.labels, labels)
  }

  test("pipeline with no concurrency group") {
    val pipeline = Pipeline.one(PipelineId("no-concurrency"), workflow1, concurrencyGroup = None)
    assertEquals(pipeline.concurrencyGroup, None)
  }

  test("pipeline with concurrency group") {
    val pipeline = Pipeline.one(
      PipelineId("concurrent"),
      workflow1,
      concurrencyGroup = Some("deployment-prod")
    )
    assertEquals(pipeline.concurrencyGroup, Some("deployment-prod"))
  }

  test("pipeline ID can contain hyphens and underscores") {
    val id1 = PipelineId("my-pipeline-123")
    val id2 = PipelineId("my_pipeline_456")
    val id3 = PipelineId("my-pipeline_789")

    assertEquals(id1.value, "my-pipeline-123")
    assertEquals(id2.value, "my_pipeline_456")
    assertEquals(id3.value, "my-pipeline_789")
  }

  test("complex pipeline with multiple workflows and dependencies") {
    val buildJob  = Job.one(JobId("build"), dummyStep)
    val testJob   = Job.one(JobId("test"), dummyStep2).needs(JobId("build"))
    val deployJob = Job.one(JobId("deploy"), dummyStep3).needs(JobId("test"))

    val ciWorkflow = Workflow.of("ci", buildJob, testJob)
    val cdWorkflow = Workflow.one("cd", deployJob)

    val pipeline = Pipeline.of(
      id = PipelineId("full-cicd"),
      first = ciWorkflow,
      rest = cdWorkflow
    )

    assertEquals(pipeline.workflows.length, 2)
    assertEquals(pipeline.workflows.head.jobs.length, 2)
    assertEquals(pipeline.workflows.tail.head.jobs.length, 1)
  }

  test("pipeline can be built incrementally") {
    val pipeline = Pipeline
      .one(PipelineId("incremental"), workflow1)
      .add(workflow2)
      .add(workflow3)

    assertEquals(pipeline.workflows.length, 3)
    assertEquals(pipeline.workflows.toVector, Vector(workflow1, workflow2, workflow3))
  }

  test("pipeline with workflow containing matrix jobs") {
    val matrixJob = Job.one(
      JobId("matrix-build"),
      dummyStep,
      matrix = Map(
        "os"      -> NonEmptyVector.of("linux", "windows", "macos"),
        "version" -> NonEmptyVector.of("14", "16", "18")
      )
    )
    val matrixWorkflow = Workflow.one("matrix-workflow", matrixJob)
    val pipeline       = Pipeline.one(PipelineId("matrix-pipeline"), matrixWorkflow)

    assertEquals(pipeline.workflows.length, 1)
    assertEquals(pipeline.workflows.head.jobs.head.matrix.size, 2)
  }

  test("pipeline with workflows having default containers") {
    val containerWorkflow = Workflow.one(
      "containerized",
      job1,
      defaultContainer = Some(Container("node:18"))
    )
    val pipeline = Pipeline.one(PipelineId("container-pipeline"), containerWorkflow)

    assertEquals(pipeline.workflows.head.defaultContainer, Some(Container("node:18")))
  }

  test("pipeline with multiple workflows with different concurrency groups") {
    val workflow1WithConcurrency = Workflow.one(
      "w1",
      job1,
      concurrencyGroup = Some("group-a")
    )
    val workflow2WithConcurrency = Workflow.one(
      "w2",
      job2,
      concurrencyGroup = Some("group-b")
    )

    val pipeline = Pipeline.of(
      PipelineId("multi-concurrency"),
      workflow1WithConcurrency,
      workflow2WithConcurrency
    )

    assertEquals(pipeline.workflows.head.concurrencyGroup, Some("group-a"))
    assertEquals(pipeline.workflows.tail.head.concurrencyGroup, Some("group-b"))
  }

  test("build complete pipeline using fluent API") {
    val checkout = Step.Checkout()(using StepMeta())
    val build    = Step.Run(Command.Exec("npm", List("run", "build")))(using StepMeta())
    val test     = Step.Run(Command.Exec("npm", List("test")))(using StepMeta())
    val deploy   = Step.Run(Command.Exec("kubectl", List("apply", "-f", "k8s/")))(using StepMeta())

    val buildJob  = Job.one(JobId("build"), checkout).~>(build)
    val testJob   = Job.one(JobId("test"), test).needs(JobId("build"))
    val deployJob = Job.one(JobId("deploy"), deploy).needs(JobId("test"))

    val ciWorkflow = Workflow.of("ci", buildJob, testJob)
    val cdWorkflow = Workflow.one("cd", deployJob)

    val pipeline = Pipeline
      .one(PipelineId("cicd"), ciWorkflow)
      .add(cdWorkflow)

    assertEquals(pipeline.workflows.length, 2)
    assertEquals(pipeline.workflows.head.name, "ci")
    assertEquals(pipeline.workflows.tail.head.name, "cd")
  }

  test("pipeline with long timeout") {
    val pipeline = Pipeline.one(
      PipelineId("long-running"),
      workflow1,
      timeout = Some(24.hours)
    )
    assertEquals(pipeline.timeout, Some(24.hours))
  }

  test("pipeline with very short timeout") {
    val pipeline = Pipeline.one(
      PipelineId("quick"),
      workflow1,
      timeout = Some(30.seconds)
    )
    assertEquals(pipeline.timeout, Some(30.seconds))
  }

  test("++ operator with empty first pipeline should still work") {
    val pipeline      = Pipeline.one(PipelineId("base"), workflow1)
    val moreWorkflows = NonEmptyVector.of(workflow2)
    val result        = pipeline ++ moreWorkflows

    assertEquals(result.workflows.length, 2)
  }

  test("add method on large pipeline") {
    var pipeline = Pipeline.one(PipelineId("large"), workflow1)
    (1 to 10).foreach { i =>
      val newJob      = Job.one(JobId(s"job-$i"), dummyStep)
      val newWorkflow = Workflow.one(s"workflow-$i", newJob)
      pipeline = pipeline.add(newWorkflow)
    }

    assertEquals(pipeline.workflows.length, 11)
  }

  test("pipeline with all optional parameters set to None") {
    val pipeline = Pipeline(
      id = PipelineId("minimal-none"),
      workflows = NonEmptyVector.one(workflow1),
      concurrencyGroup = None,
      labels = Set.empty,
      timeout = None
    )

    assertEquals(pipeline.concurrencyGroup, None)
    assertEquals(pipeline.labels, Set.empty)
    assertEquals(pipeline.timeout, None)
  }

  test("pipeline ID with numeric characters") {
    val id = PipelineId("pipeline-123-v2")
    assertEquals(id.value, "pipeline-123-v2")
  }

  test("pipeline with workflows containing different job counts") {
    val singleJobWorkflow = Workflow.one("single", job1)
    val multiJobWorkflow  = Workflow.of("multi", job1, job2, job3)

    val pipeline = Pipeline.of(
      PipelineId("varied"),
      singleJobWorkflow,
      multiJobWorkflow
    )

    assertEquals(pipeline.workflows.head.jobs.length, 1)
    assertEquals(pipeline.workflows.tail.head.jobs.length, 3)
  }

  test("pipeline preserves workflow labels") {
    val labeledWorkflow = Workflow.one("labeled", job1, labels = Set("fast", "unit"))
    val pipeline        = Pipeline.one(PipelineId("preserve-labels"), labeledWorkflow)

    assertEquals(pipeline.workflows.head.labels, Set("fast", "unit"))
  }

  test("pipeline can have same concurrency group as workflow") {
    val workflow = Workflow.one("w", job1, concurrencyGroup = Some("shared-group"))
    val pipeline = Pipeline.one(
      PipelineId("shared"),
      workflow,
      concurrencyGroup = Some("shared-group")
    )

    assertEquals(pipeline.concurrencyGroup, Some("shared-group"))
    assertEquals(workflow.concurrencyGroup, Some("shared-group"))
  }

  test("pipeline with workflow containing composite steps") {
    val compositeStep     = Step.Composite(NonEmptyVector.of(dummyStep, dummyStep2, dummyStep3))
    val compositeJob      = Job.one(JobId("composite"), compositeStep)
    val compositeWorkflow = Workflow.one("composite-workflow", compositeJob)
    val pipeline          = Pipeline.one(PipelineId("composite-pipeline"), compositeWorkflow)

    assertEquals(pipeline.workflows.head.jobs.head.steps.length, 1)
    pipeline.workflows.head.jobs.head.steps.head match
      case Step.Composite(steps) => assertEquals(steps.length, 3)
      case _                     => fail("Expected composite step")
  }

  test("pipeline equality") {
    val p1 = Pipeline.one(PipelineId("test"), workflow1)
    val p2 = Pipeline.one(PipelineId("test"), workflow1)
    val p3 = Pipeline.one(PipelineId("other"), workflow1)

    assertEquals(p1, p2)
    assert(p1 != p3)
  }

  test("pipeline with special characters in labels") {
    val labels   = Set("v1.0", "release-2024", "tag_name")
    val pipeline = Pipeline.one(PipelineId("special"), workflow1, labels = labels)
    assertEquals(pipeline.labels, labels)
  }

  test("pipeline timeout conversion") {
    val pipeline = Pipeline.one(PipelineId("convert"), workflow1, timeout = Some(90.minutes))
    assertEquals(pipeline.timeout, Some(90.minutes))
  }

  test("chaining add and ++ operators") {
    val pipeline = Pipeline
      .one(PipelineId("chain"), workflow1)
      .add(workflow2)
      .++(NonEmptyVector.one(workflow3))

    assertEquals(pipeline.workflows.length, 3)
    assertEquals(pipeline.workflows.toVector, Vector(workflow1, workflow2, workflow3))
  }

  test("pipeline with empty concurrency group string") {
    val pipeline = Pipeline.one(
      PipelineId("empty-group"),
      workflow1,
      concurrencyGroup = Some("")
    )
    assertEquals(pipeline.concurrencyGroup, Some(""))
  }

  test("pipeline copy preserves all fields") {
    val original = Pipeline(
      id = PipelineId("original"),
      workflows = NonEmptyVector.of(workflow1, workflow2),
      concurrencyGroup = Some("group"),
      labels = Set("label1", "label2"),
      timeout = Some(1.hour)
    )

    val copied = original.copy()

    assertEquals(copied.id, original.id)
    assertEquals(copied.workflows, original.workflows)
    assertEquals(copied.concurrencyGroup, original.concurrencyGroup)
    assertEquals(copied.labels, original.labels)
    assertEquals(copied.timeout, original.timeout)
  }
