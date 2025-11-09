package com.verschraenkt.ci.dsl.scala

import com.verschraenkt.ci.core.model.*
import cats.data.NonEmptyVector
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

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
    condition: Condition = Condition.Always
):
  def addStep(step: Step): JobBuilder =
    this.copy(steps = steps :+ step)

  def ~>(step: Step): JobBuilder =
    addStep(step)

  def addSteps(moreSteps: Step*): JobBuilder =
    this.copy(steps = steps ++ moreSteps)

  def run(command: CommandLike)(using meta: StepMeta = StepMeta()): JobBuilder =
    addStep(Step.Run(command)(using meta))

  def checkout()(using meta: StepMeta = StepMeta()): JobBuilder =
    addStep(Step.Checkout()(using meta))

  def restoreCache(cache: CacheLike, paths: String*)(using meta: StepMeta = StepMeta()): JobBuilder =
    addStep(Step.RestoreCache(cache, cats.data.NonEmptyList.fromListUnsafe(paths.toList))(using meta))

  def saveCache(cache: CacheLike, paths: String*)(using meta: StepMeta = StepMeta()): JobBuilder =
    addStep(Step.SaveCache(cache, cats.data.NonEmptyList.fromListUnsafe(paths.toList))(using meta))

  def withResource(resource: Resource): JobBuilder =
    this.copy(resources = resource)

  def withTimeout(duration: FiniteDuration): JobBuilder =
    this.copy(timeout = duration)

  def withContainer(containerConfig: Container): JobBuilder =
    this.copy(container = Some(containerConfig))

  def withLabel(label: String): JobBuilder =
    this.copy(labels = labels + label)

  def withLabels(newLabels: Set[String]): JobBuilder =
    this.copy(labels = labels ++ newLabels)

  def withConcurrencyGroup(group: String): JobBuilder =
    this.copy(concurrencyGroup = Some(group))

  def withCondition(cond: Condition): JobBuilder =
    this.copy(condition = cond)

  def dependsOn(jobIds: JobId*): JobBuilder =
    this.copy(dependencies = dependencies ++ jobIds.toSet)

  def needs(jobIds: JobId*): JobBuilder =
    dependsOn(jobIds*)

  def withMatrix(matrixConfig: Map[String, NonEmptyVector[String]]): JobBuilder =
    this.copy(matrix = matrixConfig)

  def build(): Job =
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

object JobBuilder:
  def apply(name: String): JobBuilder =
    new JobBuilder(name)

  def apply(name: String, firstStep: Step, rest: Step*): JobBuilder =
    new JobBuilder(name, steps = firstStep +: rest.toVector)
