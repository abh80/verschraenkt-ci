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

final case class Workflow(
    name: String,
    jobs: NonEmptyVector[Job],
    defaultContainer: Option[Container] = None,
    concurrencyGroup: Option[String] = None,
    labels: Set[String] = Set.empty,
    condition: Condition = Condition.Always
)

object Workflow:
  def one(
      name: String,
      job: Job,
      defaultContainer: Option[Container] = None,
      concurrencyGroup: Option[String] = None,
      labels: Set[String] = Set.empty,
      condition: Condition = Condition.Always
  ): Workflow =
    Workflow(name, NonEmptyVector.one(job), defaultContainer, concurrencyGroup, labels, condition)

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
  def addJob(job: Job): Workflow = w.copy(jobs = NonEmptyVector.fromVectorUnsafe(w.jobs.toVector :+ job))

  def addJobs(more: NonEmptyVector[Job]): Workflow =
    w.copy(jobs = NonEmptyVector.fromVectorUnsafe(w.jobs.toVector ++ more.toVector))

  def materialized: NonEmptyVector[Job] =
    Workflow.materialize(w)
