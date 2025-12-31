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
package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import _root_.com.verschraenkt.ci.core.model.{Condition, Container, Job, Workflow}

final class WorkflowBuilder(val name: String):
  private var jobsVec: Vector[Job]                   = Vector.empty
  private var defaultContainerOpt: Option[Container] = None
  private var concurrencyGroupOpt: Option[String]    = None
  private var labelsSet: Set[String]                 = Set.empty
  private var conditionVal: Condition                = Condition.Always

  def addJob(j: Job): Unit                            = jobsVec :+= j
  def setDefaultContainer(container: Container): Unit = defaultContainerOpt = Some(container)
  def setConcurrency(group: String): Unit             = concurrencyGroupOpt = Some(group)
  def addLabels(newLabels: String*): Unit             = labelsSet ++= newLabels
  def setCondition(newCondition: Condition): Unit     = conditionVal = newCondition

  private[sc] def build(): Workflow =
    require(jobsVec.nonEmpty, s"Workflow '$name' must have at least one job")
    Workflow(
      name = name,
      jobs = NonEmptyVector.fromVectorUnsafe(jobsVec),
      defaultContainer = defaultContainerOpt,
      concurrencyGroup = concurrencyGroupOpt,
      labels = labelsSet,
      condition = conditionVal
    )
