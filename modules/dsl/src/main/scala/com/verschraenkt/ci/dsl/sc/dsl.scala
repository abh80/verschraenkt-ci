package com.verschraenkt.ci.dsl.sc

import _root_.com.verschraenkt.ci.core.model.*
import cats.data.NonEmptyVector
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

object dsl:
  extension (n: Int) def GB: Long = (n.toLong * 953.674316).toLong // in MiB

  // --- Top-level DSL functions ---

  def pipeline(name: String)(block: PipelineBuilder ?=> Unit): Pipeline =
    given builder: PipelineBuilder = new PipelineBuilder(PipelineId(name))
    block
    builder.build()

  def workflow(name: String)(block: WorkflowBuilder ?=> Unit)(using p: PipelineBuilder): Unit =
    given builder: WorkflowBuilder = new WorkflowBuilder(name)
    block
    p.addWorkflow(builder.build())

  def job(name: String)(block: JobBuilder ?=> Unit)(using w: WorkflowBuilder): Unit =
    given builder: JobBuilder = new JobBuilder(name)
    block
    w.addJob(builder.build())

  // --- Pipeline-level functions (using PipelineBuilder) ---

  def timeout(duration: FiniteDuration)(using p: PipelineBuilder): Unit =
    p.setTimeout(duration)

  def labels(newLabels: String*)(using p: PipelineBuilder): Unit =
    p.addLabels(newLabels*)

  def concurrency(group: String)(using p: PipelineBuilder): Unit =
    p.setConcurrency(group)

  // --- Workflow-level functions (using WorkflowBuilder) ---

  def defaultContainer(
      image: String,
      args: List[String] = Nil,
      env: Map[String, String] = Map.empty,
      user: Option[String] = None,
      workdir: Option[String] = None
  )(using w: WorkflowBuilder): Unit =
    w.setDefaultContainer(Container(image, args, env, user, workdir))

  def workflowCondition(cond: Condition)(using w: WorkflowBuilder): Unit =
    w.setCondition(cond)

  // --- Job-level functions (using JobBuilder) ---

  def steps(block: StepsBuilder ?=> Unit)(using j: JobBuilder): Unit =
    given builder: StepsBuilder = new StepsBuilder
    block
    builder.result.foreach(j.addStep)

  def needs(jobIds: JobId*)(using j: JobBuilder): Unit =
    j.addDependencies(jobIds*)

  def jobContainer(
      image: String,
      args: List[String] = Nil,
      env: Map[String, String] = Map.empty,
      user: Option[String] = None,
      workdir: Option[String] = None
  )(using j: JobBuilder): Unit =
    j.setContainer(Container(image, args, env, user, workdir))

  def jobLabels(newLabels: String*)(using j: JobBuilder): Unit =
    j.addLabels(newLabels*)

  def jobConcurrency(group: String)(using j: JobBuilder): Unit =
    j.setConcurrencyGroup(group)

  def jobCondition(cond: Condition)(using j: JobBuilder): Unit =
    j.setCondition(cond)

  def jobTimeout(duration: FiniteDuration)(using j: JobBuilder): Unit =
    j.setTimeout(duration)

  def resources(cpuMilli: Int = 1000, memoryMiB: Int = 512, gpu: Int = 0, diskMiB: Int = 0)(using
      j: JobBuilder
  ): Unit =
    j.setResources(Resource(cpuMilli, memoryMiB, gpu, diskMiB))

  def matrix(matrixConfig: Map[String, NonEmptyVector[String]])(using j: JobBuilder): Unit =
    j.setMatrix(matrixConfig)

  // --- Step-level functions (using StepsBuilder) ---

  def checkout()(using sb: StepsBuilder): Unit =
    sb.add(StepLike.Checkout)

  def run(shellCommand: String, shell: ShellKind = ShellKind.Sh)(using f: Meta = identity)(using
      sb: StepsBuilder
  ): Unit =
    sb.add(StepLike.Run(shellCommand, shell, f))
