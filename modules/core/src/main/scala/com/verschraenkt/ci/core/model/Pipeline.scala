package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector
import scala.concurrent.duration.*

final case class PipelineId(value: String) extends AnyVal

final case class Pipeline(
    id: PipelineId,
    workflows: NonEmptyVector[Workflow],
    concurrencyGroup: Option[String] = None,
    labels: Set[String] = Set.empty,
    timeout: Option[FiniteDuration] = None
)

object Pipeline:
  def one(
      id: PipelineId,
      w: Workflow,
      concurrencyGroup: Option[String] = None,
      labels: Set[String] = Set.empty,
      timeout: Option[FiniteDuration] = None
  ): Pipeline =
    Pipeline(id, NonEmptyVector.one(w), concurrencyGroup, labels, timeout)

  def of(id: PipelineId, first: Workflow, rest: Workflow*): Pipeline =
    Pipeline(id, NonEmptyVector(first, rest.toVector))

extension (p: Pipeline)
  def add(w: Workflow): Pipeline =
    p.copy(workflows = NonEmptyVector.fromVectorUnsafe(p.workflows.toVector :+ w))

  def ++(ws: NonEmptyVector[Workflow]): Pipeline =
    p.copy(workflows = NonEmptyVector.fromVectorUnsafe(p.workflows.toVector ++ ws.toVector))
