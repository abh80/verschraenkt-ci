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

/** Represents a unique identifier for a pipeline.
  *
  * @param value
  *   The string value of the pipeline ID.
  */
final case class PipelineId(value: String) extends AnyVal

/** Represents a pipeline in the CI system, consisting of a unique ID, a non-empty sequence of workflows, and
  * optional configuration parameters such as concurrency group, labels, and timeout.
  *
  * @param id
  *   The unique identifier for the pipeline.
  * @param workflows
  *   A non-empty vector of workflows that define the steps of the pipeline.
  * @param concurrencyGroup
  *   An optional string specifying a concurrency group to limit simultaneous executions.
  * @param labels
  *   A set of string labels associated with the pipeline for categorization or filtering.
  * @param timeout
  *   An optional finite duration specifying the maximum time allowed for pipeline execution.
  */
final case class Pipeline(
    id: PipelineId,
    workflows: NonEmptyVector[Workflow],
    concurrencyGroup: Option[String] = None,
    labels: Set[String] = Set.empty,
    timeout: Option[FiniteDuration] = None
)

object Pipeline:
  /** Creates a pipeline containing a single workflow.
    *
    * @param id
    *   The unique identifier for the pipeline.
    * @param w
    *   The single workflow to include in the pipeline.
    * @param concurrencyGroup
    *   An optional concurrency group for the pipeline.
    * @param labels
    *   A set of labels for the pipeline.
    * @param timeout
    *   An optional timeout duration for the pipeline.
    * @return
    *   A new Pipeline instance with the specified workflow.
    */
  def one(
      id: PipelineId,
      w: Workflow,
      concurrencyGroup: Option[String] = None,
      labels: Set[String] = Set.empty,
      timeout: Option[FiniteDuration] = None
  ): Pipeline =
    Pipeline(id, NonEmptyVector.one(w), concurrencyGroup, labels, timeout)

  /** Creates a pipeline with one or more workflows.
    *
    * @param id
    *   The unique identifier for the pipeline.
    * @param first
    *   The first workflow in the pipeline.
    * @param rest
    *   Additional workflows to include in the pipeline.
    * @return
    *   A new Pipeline instance containing the specified workflows.
    */
  def of(id: PipelineId, first: Workflow, rest: Workflow*): Pipeline =
    Pipeline(id, NonEmptyVector(first, rest.toVector))

extension (p: Pipeline)
  /** Adds a single workflow to the existing pipeline.
    *
    * @param w
    *   The workflow to append to the pipeline's workflows.
    * @return
    *   A new Pipeline instance with the added workflow.
    */
  def add(w: Workflow): Pipeline =
    p.copy(workflows = NonEmptyVector.fromVectorUnsafe(p.workflows.toVector :+ w))

  /** Concatenates a non-empty vector of workflows to the existing pipeline.
    *
    * @param ws
    *   The workflows to append to the pipeline's workflows.
    * @return
    *   A new Pipeline instance with the concatenated workflows.
    */
  def ++(ws: NonEmptyVector[Workflow]): Pipeline =
    p.copy(workflows = NonEmptyVector.fromVectorUnsafe(p.workflows.toVector ++ ws.toVector))
