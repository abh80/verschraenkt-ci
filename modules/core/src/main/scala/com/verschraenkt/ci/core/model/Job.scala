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

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt

/** Represents a unique identifier for a job in the CI system
  *
  * @param value
  *   The string value of the job identifier
  */
final case class JobId(value: String)

/** Represents a CI job with its configuration and execution steps
  *
  * @param id
  *   Unique identifier for the job
  * @param steps
  *   Non-empty sequence of steps to be executed in order
  * @param dependencies
  *   Set of job IDs that must complete before this job can start
  * @param resources
  *   Computational resources required for the job
  * @param timeout
  *   Maximum duration allowed for job completion
  * @param matrix
  *   Configuration for matrix builds, mapping variables to their possible values
  * @param container
  *   Optional container configuration for job isolation
  * @param labels
  *   Set of labels for job categorization and filtering
  * @param concurrencyGroup
  *   Optional group name for controlling concurrent execution
  */
final case class Job(
    id: JobId,
    steps: NonEmptyVector[Step],
    dependencies: Set[JobId] = Set.empty,
    resources: Resource = Resource(1000, 512, 0, 0),
    timeout: FiniteDuration,
    matrix: Map[String, NonEmptyVector[String]] = Map.empty,
    container: Option[Container],
    labels: Set[String] = Set.empty,
    concurrencyGroup: Option[String] = None,
    condition: Condition = Condition.Always
):
  def ~>(step: Step): Job =
    this.copy(steps = NonEmptyVector.fromVectorUnsafe(this.steps.toVector :+ step))

  def ~>(more: NonEmptyVector[Step]): Job =
    this.copy(steps = NonEmptyVector.fromVectorUnsafe(this.steps.toVector ++ more.toVector))

  def needs(ids: JobId*): Job =
    this.copy(dependencies = this.dependencies ++ ids.toSet)

/** Companion object for Job class providing convenient factory methods for job creation */
object Job:
  /** Creates a Job with a single step
    *
    * @param id
    *   Unique identifier for the job
    * @param step
    *   Single step to be executed
    * @param needs
    *   Set of job dependencies
    * @param resources
    *   Required computational resources
    * @param timeout
    *   Maximum execution duration
    * @param matrix
    *   Matrix build configuration
    * @param container
    *   Optional container configuration
    * @param labels
    *   Job labels
    * @param concurrencyGroup
    *   Optional concurrency control group
    * @return
    *   A new Job instance with the specified configuration
    */
  def one(
      id: JobId,
      step: Step,
      needs: Set[JobId] = Set.empty,
      resources: Resource = Resource(1000, 512, 0, 0),
      timeout: FiniteDuration = 30.minutes,
      matrix: Map[String, NonEmptyVector[String]] = Map.empty,
      container: Option[Container] = None,
      labels: Set[String] = Set.empty,
      concurrencyGroup: Option[String] = None,
      condition: Condition = Condition.Always
  ): Job =
    Job(
      id,
      NonEmptyVector.one(step),
      needs,
      resources,
      timeout,
      matrix,
      container,
      labels,
      concurrencyGroup,
      condition
    )

  /** Creates a Job with multiple steps
    *
    * @param id
    *   Unique identifier for the job
    * @param first
    *   First step to be executed
    * @param rest
    *   Additional steps to be executed in sequence
    * @param timeout
    *   Maximum execution duration
    * @param container
    *   Optional container configuration
    * @return
    *   A new Job instance with the specified steps and configuration
    */
  def of(
      id: JobId,
      first: Step,
      rest: Step*
  )(
      timeout: FiniteDuration = 30.minutes,
      container: Option[Container] = None,
      condition: Condition = Condition.Always
  ): Job = Job(
    id,
    NonEmptyVector(first, rest.toVector),
    timeout = timeout,
    container = container,
    condition = condition
  )
