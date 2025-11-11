package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.{Condition, Container, Job, JobId, Resource, Step, StepMeta}

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

final case class JobBuilder(
    name: String,
    steps: Vector[Step] = Vector.empty,
    dependencies: Set[JobId] = Set.empty,
    resources: Resource = Resource(1000, 512, 0, 0),
    timeout: FiniteDuration = 30.minutes,
    matrix: Map[String, NonEmptyVector[String]] = Map.empty,
    container: Option[Container] = None,
    labels: Set[String] = Set.empty,
    concurrencyGroup: Option[String] = None,
    condition: Condition = Condition.Always,
    stepMeta: StepMeta = StepMeta()
):

  private def withResource(resource: Resource): JobBuilder =
    this.copy(resources = resource)

  private def withTimeout(duration: FiniteDuration): JobBuilder =
    this.copy(timeout = duration)

  private def withContainer(containerConfig: Container): JobBuilder =
    this.copy(container = Some(containerConfig))

  def executor(exec: Executor): JobBuilder = withContainer(exec.toContainer)

  def labels(moreLabels: String*): JobBuilder = this.copy(labels = labels ++ moreLabels)

  def concurrencyGroup(group: String): JobBuilder =
    this.copy(concurrencyGroup = Some(group))

  def withCondition(cond: Condition): JobBuilder =
    this.copy(condition = cond)

  def dependsOn(jobIds: JobId*): JobBuilder =
    this.copy(dependencies = dependencies ++ jobIds.toSet)

  def needs(jobIds: JobId*): JobBuilder =
    dependsOn(jobIds*)

  def withMatrix(matrixConfig: Map[String, NonEmptyVector[String]]): JobBuilder =
    this.copy(matrix = matrixConfig)

  def steps(stepLike: StepLike*): JobBuilder =
    this.copy(steps = steps ++ stepLike.map(StepBuilder.toStep(_)(using stepMeta)))

  def timeout(duration: FiniteDuration): JobBuilder = this.copy(timeout = duration)

  private def build(): Job =
    require(steps.nonEmpty, s"Job '$name' must have at least one step")
    Job(
      id = JobId(name),
      steps = NonEmptyVector.fromVectorUnsafe(steps),
      dependencies = dependencies,
      resources = resources,
      timeout = timeout,
      matrix = matrix,
      container = container,
      labels = labels,
      concurrencyGroup = concurrencyGroup,
      condition = condition
    )