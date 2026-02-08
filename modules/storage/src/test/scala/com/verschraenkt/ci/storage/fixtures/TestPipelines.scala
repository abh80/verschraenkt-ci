package com.verschraenkt.ci.storage.fixtures

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.*
import com.verschraenkt.ci.storage.db.codecs.User

import scala.concurrent.duration.*
import scala.language.postfixOps

/** Reusable test data for storage tests */
object TestPipelines:

  // Using this to get a separate id for each test pipeline to avoid scenarios to run fresh db for each test
  private var counter = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  val testUser: User    = User("test-user")
  val anotherUser: User = User("another-user")

  /** Simple pipeline with one workflow and one job */
  def simplePipeline: Pipeline =
    val step     = Step.Checkout()(using StepMeta(id = Some("checkout-1")))
    val job      = Job.one(JobId("job-1"), step)
    val workflow = Workflow.one("simple-workflow", job)

    Pipeline(
      id = PipelineId(s"simple-pipeline-$getCounter"),
      workflows = NonEmptyVector.one(workflow),
      labels = Set.empty,
      timeout = None,
      concurrencyGroup = None
    )

  /** Pipeline with multiple labels */
  def pipelineWithLabels: Pipeline =
    val step     = Step.Run(Command.Shell("npm test"))(using StepMeta(id = Some("test-1")))
    val job      = Job.one(JobId("test-job"), step)
    val workflow = Workflow.one("test-workflow", job)

    Pipeline(
      id = PipelineId(s"labeled-pipeline-$getCounter"),
      workflows = NonEmptyVector.one(workflow),
      labels = Set("production", "automated", "critical"),
      timeout = Some(30.minutes),
      concurrencyGroup = Some("ci-group")
    )

  /** Pipeline with multiple workflows */
  def pipelineWithMultipleWorkflows: Pipeline =
    val buildStep = Step.Run(Command.Shell("npm run build"))(using StepMeta(id = Some("build")))
    val testStep  = Step.Run(Command.Shell("npm test"))(using StepMeta(id = Some("test")))

    val buildJob = Job.one(JobId("build"), buildStep)
    val testJob  = Job.one(JobId("test"), testStep)

    val buildWorkflow = Workflow.one("build-workflow", buildJob)
    val testWorkflow  = Workflow.one("test-workflow", testJob)

    Pipeline(
      id = PipelineId(s"multi-workflow-pipeline-$getCounter"),
      workflows = NonEmptyVector.of(buildWorkflow, testWorkflow),
      labels = Set("integration"),
      timeout = Some(1.hour),
      concurrencyGroup = None
    )

  /** Pipeline with complex step configuration */
  def complexPipeline: Pipeline =
    given meta: StepMeta = StepMeta(
      id = Some("complex-step"),
      continueOnError = true,
      when = When.OnSuccess,
      timeout = Some(10.minutes)
    )

    val execStep = Step.Run(
      Command.Exec(
        program = "docker",
        args = List("build", "-t", "myapp", "."),
        env = Map("DOCKER_BUILDKIT" -> "1"),
        cwd = Some("/app"),
        timeoutSec = Some(300)
      )
    )

    val job = Job(
      id = JobId("docker-build"),
      steps = NonEmptyVector.one(execStep),
      container = Some(Container(image = "docker:latest")),
      resources = Resource(4, memoryMiB = 8192, diskMiB = 20000),
      env = Map("CI" -> "true"),
      timeout = 20.minutes
    )

    val workflow = Workflow(
      name = "Docker Build",
      jobs = NonEmptyVector.one(job),
      env = Map.empty
    )

    Pipeline(
      id = PipelineId(s"complex-pipeline-$getCounter"),
      workflows = NonEmptyVector.one(workflow),
      labels = Set("docker", "build"),
      timeout = Some(2.hours),
      concurrencyGroup = Some("docker-builds")
    )

  /** Create a pipeline with a custom ID */
  def withId(id: String): Pipeline =
    simplePipeline.copy(id = PipelineId(id))

  /** Create a pipeline with custom labels */
  def withLabels(labels: Set[String]): Pipeline =
    simplePipeline.copy(labels = labels)
