package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import _root_.com.verschraenkt.ci.core.model.{ Pipeline, PipelineId, Workflow }

import scala.concurrent.duration.FiniteDuration

final class PipelineBuilder(val id: PipelineId):
  private var workflowsVec: Vector[Workflow]      = Vector.empty
  private var concurrencyGroupOpt: Option[String] = None
  private var labelsSet: Set[String]              = Set.empty
  private var timeoutOpt: Option[FiniteDuration]  = None

  def addWorkflow(w: Workflow): Unit             = workflowsVec :+= w
  def setConcurrency(group: String): Unit        = concurrencyGroupOpt = Some(group)
  def addLabels(newLabels: String*): Unit        = labelsSet ++= newLabels
  def setTimeout(duration: FiniteDuration): Unit = timeoutOpt = Some(duration)

  def build(): Pipeline =
    require(workflowsVec.nonEmpty, s"Pipeline '${id.value}' must have at least one workflow")
    Pipeline(
      id = id,
      workflows = NonEmptyVector.fromVectorUnsafe(workflowsVec),
      concurrencyGroup = concurrencyGroupOpt,
      labels = labelsSet,
      timeout = timeoutOpt
    )
