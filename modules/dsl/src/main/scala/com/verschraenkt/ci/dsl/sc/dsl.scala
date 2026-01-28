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

  def meta(f: Meta)(body: StepsBuilder ?=> Unit)(using sb: StepsBuilder): Unit =
    // Temporarily modify sb.stepMeta
    val oldMeta = sb.stepMeta.meta
    sb.stepMeta.meta = sb.stepMeta.meta.andThen(f)
    try
      body(using sb) // Execute body, explicitly passing 'sb' as implicit
    finally
      sb.stepMeta.meta = oldMeta // Restore old meta

  def checkout()(using sb: StepsBuilder): Unit =
    sb.add(StepLike.Checkout)

  // --- Cache functions ---

  def restoreCache(key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restoreCache requires at least one path"))
    sb.add(StepLike.RestoreCache(Cache.RestoreCache(cacheKey, nonEmptyPaths), nonEmptyPaths))

  def saveCache(key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("saveCache requires at least one path"))
    sb.add(StepLike.SaveCache(Cache.SaveCache(cacheKey, nonEmptyPaths), nonEmptyPaths))

  def restoreBranchCache(key: String, branch: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restoreBranchCache requires at least one path"))
    val cache = Cache.restoreForBranch(cacheKey, nonEmptyPaths, branch)
    sb.add(StepLike.RestoreCache(cache, nonEmptyPaths))

  def saveBranchCache(key: String, branch: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("saveBranchCache requires at least one path"))
    val cache = Cache.saveForBranch(cacheKey, nonEmptyPaths, branch)
    sb.add(StepLike.SaveCache(cache, nonEmptyPaths))

  def restorePRCache(key: String, pr: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restorePRCache requires at least one path"))
    val cache = Cache.restoreForPR(cacheKey, nonEmptyPaths, pr)
    sb.add(StepLike.RestoreCache(cache, nonEmptyPaths))

  def savePRCache(key: String, pr: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("savePRCache requires at least one path"))
    val cache = Cache.saveForPR(cacheKey, nonEmptyPaths, pr)
    sb.add(StepLike.SaveCache(cache, nonEmptyPaths))

  def restoreTagCache(key: String, tag: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restoreTagCache requires at least one path"))
    val cache = Cache.restoreForTag(cacheKey, nonEmptyPaths, tag)
    sb.add(StepLike.RestoreCache(cache, nonEmptyPaths))

  def saveTagCache(key: String, tag: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("saveTagCache requires at least one path"))
    val cache = Cache.saveForTag(cacheKey, nonEmptyPaths, tag)
    sb.add(StepLike.SaveCache(cache, nonEmptyPaths))

  // --- External cache functions ---

  def restoreS3Cache(bucket: String, region: String, key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restoreS3Cache requires at least one path"))
    val cache = S3Cache(bucket, region, cacheKey)
    sb.add(StepLike.RestoreCache(cache, nonEmptyPaths))

  def saveS3Cache(bucket: String, region: String, key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("saveS3Cache requires at least one path"))
    val cache = S3Cache(bucket, region, cacheKey)
    sb.add(StepLike.SaveCache(cache, nonEmptyPaths))

  def restoreGCSCache(bucket: String, key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("restoreGCSCache requires at least one path"))
    val cache = GCSCache(bucket, cacheKey)
    sb.add(StepLike.RestoreCache(cache, nonEmptyPaths))

  def saveGCSCache(bucket: String, key: String, paths: String*)(using sb: StepsBuilder): Unit =
    import cats.data.NonEmptyList
    val cacheKey = CacheKey.literal(key)
    val nonEmptyPaths = NonEmptyList.fromList(paths.toList)
      .getOrElse(throw new IllegalArgumentException("saveGCSCache requires at least one path"))
    val cache = GCSCache(bucket, cacheKey)
    sb.add(StepLike.SaveCache(cache, nonEmptyPaths))

  // Original curried method (keep as-is, with defaults)
  def run(
    shellCommand: String,
    shell: ShellKind = ShellKind.Sh
  )(using sb: StepsBuilder): AddedStep =
    val combinedMeta = sb.stepMeta.meta
    sb.add(StepLike.Run(shellCommand, shell, combinedMeta))
    AddedStep(sb)