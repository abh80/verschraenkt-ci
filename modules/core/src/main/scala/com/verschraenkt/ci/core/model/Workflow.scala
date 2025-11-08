package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector

final case class Workflow(
    name: String,
    jobs: NonEmptyVector[Job],
    defaultContainer: Option[Container] = None,
    concurrencyGroup: Option[String] = None,
    labels: Set[String] = Set.empty
)

object Workflow:
  def one(
      name: String,
      job: Job,
      defaultContainer: Option[Container] = None,
      concurrencyGroup: Option[String] = None,
      labels: Set[String] = Set.empty
  ): Workflow =
    Workflow(name, NonEmptyVector.one(job), defaultContainer, concurrencyGroup, labels)

  def of(name: String, first: Job, rest: Job*): Workflow =
    Workflow(name, NonEmptyVector(first, rest.toVector))

  def materialize(w: Workflow): NonEmptyVector[Job] =
    w.jobs.map { j =>
      j.copy(
        container = j.container.orElse(w.defaultContainer),
        labels = j.labels ++ w.labels
      )
    }

extension (w: Workflow)
  def add(job: Job): Workflow =
    w.copy(jobs = NonEmptyVector.fromVectorUnsafe(w.jobs.toVector :+ job))

  def ++(more: NonEmptyVector[Job]): Workflow =
    w.copy(jobs = NonEmptyVector.fromVectorUnsafe(w.jobs.toVector ++ more.toVector))

  def materialized: NonEmptyVector[Job] =
    Workflow.materialize(w)