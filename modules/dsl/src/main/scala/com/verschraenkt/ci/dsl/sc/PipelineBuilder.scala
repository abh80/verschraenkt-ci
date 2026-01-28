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
import _root_.com.verschraenkt.ci.core.model.{ Pipeline, PipelineId, Workflow }

import scala.concurrent.duration.FiniteDuration

final class PipelineBuilder(val id: PipelineId):
  private var workflowsVec: Vector[Workflow]      = Vector.empty
  private var concurrencyGroupOpt: Option[String] = None
  private var labelsSet: Set[String]              = Set.empty
  private var timeoutOpt: Option[FiniteDuration]  = None

  def addWorkflow(w: Workflow): Unit             = synchronized { workflowsVec :+= w }
  def setConcurrency(group: String): Unit        = synchronized { concurrencyGroupOpt = Some(group) }
  def addLabels(newLabels: String*): Unit        = synchronized { labelsSet ++= newLabels }
  def setTimeout(duration: FiniteDuration): Unit = synchronized { timeoutOpt = Some(duration) }

  def build(): Pipeline =
    require(workflowsVec.nonEmpty, s"Pipeline '${id.value}' must have at least one workflow")
    Pipeline(
      id = id,
      workflows = NonEmptyVector.fromVectorUnsafe(workflowsVec),
      concurrencyGroup = concurrencyGroupOpt,
      labels = labelsSet,
      timeout = timeoutOpt
    )
