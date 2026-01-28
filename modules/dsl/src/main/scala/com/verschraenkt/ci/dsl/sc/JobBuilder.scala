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
import _root_.com.verschraenkt.ci.core.model.{Condition, Container, Job, JobId, Resource, Step, StepMeta}

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

final class JobBuilder(val name: String):
  private var stepsVec: Vector[Step]                         = Vector.empty
  private var dependenciesSet: Set[JobId]                    = Set.empty
  private var resourcesVal: Resource                         = Resource(1000, 512, 0, 0)
  private var timeoutVal: FiniteDuration                     = 30.minutes
  private var matrixMap: Map[String, NonEmptyVector[String]] = Map.empty
  private var containerOpt: Option[Container]                = None
  private var labelsSet: Set[String]                         = Set.empty
  private var concurrencyGroupOpt: Option[String]            = None
  private var conditionVal: Condition                        = Condition.Always
  private val stepMetaVal: StepMeta = StepMeta() // Keep stepMeta as val, configure it via StepLike later

  // Mutator methods
  def addStep(step: StepLike): Unit = synchronized {
    given sb: StepsBuilder = StepsBuilder()
    stepsVec :+= StepBuilder.toStep(step)(using stepMetaVal, sb)
  }
  def addDependencies(jobIds: JobId*): Unit      = synchronized { dependenciesSet ++= jobIds.toSet }
  def setResources(resource: Resource): Unit     = synchronized { resourcesVal = resource }
  def setTimeout(duration: FiniteDuration): Unit = synchronized { timeoutVal = duration }
  def setMatrix(matrixConfig: Map[String, NonEmptyVector[String]]): Unit = synchronized { matrixMap = matrixConfig }
  def setContainer(containerConfig: Container): Unit = synchronized { containerOpt = Some(containerConfig) }
  def addLabels(moreLabels: String*): Unit           = synchronized { labelsSet ++= moreLabels }
  def setConcurrencyGroup(group: String): Unit       = synchronized { concurrencyGroupOpt = Some(group) }
  def setCondition(cond: Condition): Unit            = synchronized { conditionVal = cond }

  private[sc] def build(): Job =
    require(stepsVec.nonEmpty, s"Job '$name' must have at least one step")
    Job(
      id = JobId(name),
      steps = NonEmptyVector.fromVectorUnsafe(stepsVec),
      dependencies = dependenciesSet,
      resources = resourcesVal,
      timeout = timeoutVal,
      matrix = matrixMap,
      container = containerOpt,
      labels = labelsSet,
      concurrencyGroup = concurrencyGroupOpt,
      condition = conditionVal
    )
