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

import cats.data.NonEmptyList
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*

class StepBuilderSpec extends FunSuite:

  private given defaultStepMeta: StepMeta = StepMeta(
    id = None,
    when = When.Always,
    timeout = None,
    continueOnError = false,
    retry = None,
    env = Map.empty,
    workingDirectory = None
  )

  test("StepBuilder should convert StepLike.Run to Step.Run with default meta") {
    given StepsBuilder = new StepsBuilder()
    val runStepLike    = StepLike.Run("ls -la")
    val step           = StepBuilder.toStep(runStepLike)

    step match
      case Step.Run(Command.Shell(script, shell, env, cwd, timeoutSec)) =>
        assertEquals(script, "ls -la")
        assertEquals(shell, ShellKind.Sh)
        assertEquals(env, Map.empty)
        assertEquals(cwd, None)
        assertEquals(timeoutSec, None)
      case other => fail(s"Expected Step.Run, but got ${other.toString}")
  }

  test("StepBuilder should convert StepLike.Run to Step.Run with custom meta from builder function") {
    given StepsBuilder = new StepsBuilder()
    val runStepLike = StepLike.Run(
      shellCommand = "docker build",
      f = _.workingDir("/app").env("BUILD_ENV" -> "production").timeout(5.minutes)
    )
    val step = StepBuilder.toStep(runStepLike)

    step match
      case Step.Run(Command.Shell(script, shell, env, cwd, timeoutSec)) =>
        assertEquals(script, "docker build")
        assertEquals(shell, ShellKind.Sh)
        assertEquals(env, Map("BUILD_ENV" -> "production"))
        assertEquals(cwd, Some("/app"))
        assertEquals(timeoutSec, Some(300)) // 5.minutes to seconds
      case other => fail(s"Expected Step.Run, but got ${other.toString}")
  }

  test("StepBuilder should convert StepLike.Run to Step.Run with custom shell") {
    given StepsBuilder = new StepsBuilder()
    val runStepLike    = StepLike.Run("pwsh -Command 'Write-Host \"Hello\"'", ShellKind.Pwsh)
    val step           = StepBuilder.toStep(runStepLike)

    step match
      case Step.Run(Command.Shell(script, shell, _, _, _)) =>
        assertEquals(script, "pwsh -Command 'Write-Host \"Hello\"'")
        assertEquals(shell, ShellKind.Pwsh)
      case other => fail(s"Expected Step.Run, but got ${other.toString}")
  }

  test("StepBuilder should convert StepLike.Checkout to Step.Checkout") {
    given StepsBuilder   = new StepsBuilder()
    val checkoutStepLike = StepLike.Checkout
    val step             = StepBuilder.toStep(checkoutStepLike)
    assertEquals(step, Step.Checkout()(using defaultStepMeta))
  }

  test("StepBuilder should convert StepLike.RestoreCache to Step.RestoreCache") {
    given StepsBuilder       = new StepsBuilder()
    val cache                = Cache.RestoreCache(CacheKey.literal("my-cache"), NonEmptyList.one("dummy"))
    val paths                = NonEmptyList.one("path/to/cache")
    val restoreCacheStepLike = StepLike.RestoreCache(cache, paths)
    val step                 = StepBuilder.toStep(restoreCacheStepLike)
    assertEquals(step, Step.RestoreCache(cache, paths)(using defaultStepMeta))
  }

  test("StepBuilder should convert StepLike.SaveCache to Step.SaveCache") {
    given StepsBuilder    = new StepsBuilder()
    val cache             = Cache.SaveCache(CacheKey.literal("my-cache"), NonEmptyList.one("dummy"))
    val paths             = NonEmptyList.one("path/to/cache")
    val saveCacheStepLike = StepLike.SaveCache(cache, paths)
    val step              = StepBuilder.toStep(saveCacheStepLike)
    assertEquals(step, Step.SaveCache(cache, paths)(using defaultStepMeta))
  }

  test("DSL restoreCache should create RestoreCache StepLike") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    restoreCache("deps", "node_modules", "package-lock.json")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.RestoreCache(cache, paths) =>
        assertEquals(cache.key.value, "deps")
        assertEquals(paths.toList, List("node_modules", "package-lock.json"))
      case other => fail(s"Expected StepLike.RestoreCache, but got ${other.toString}")
  }

  test("DSL saveCache should create SaveCache StepLike") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    saveCache("build", "target", "dist")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.SaveCache(cache, paths) =>
        assertEquals(cache.key.value, "build")
        assertEquals(paths.toList, List("target", "dist"))
      case other => fail(s"Expected StepLike.SaveCache, but got ${other.toString}")
  }

  test("DSL restoreBranchCache should create branch-scoped RestoreCache") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    restoreBranchCache("deps", "main", "node_modules")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.RestoreCache(cache, paths) =>
        assertEquals(cache.key.value, "main:deps")
        assertEquals(cache.scope, CacheScope.Branch)
        assertEquals(paths.toList, List("node_modules"))
      case other => fail(s"Expected StepLike.RestoreCache, but got ${other.toString}")
  }

  test("DSL savePRCache should create PR-scoped SaveCache") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    savePRCache("build", "123", "target")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.SaveCache(cache, paths) =>
        assertEquals(cache.key.value, "123:build")
        assertEquals(cache.scope, CacheScope.PullRequest)
        assertEquals(paths.toList, List("target"))
      case other => fail(s"Expected StepLike.SaveCache, but got ${other.toString}")
  }

  test("DSL restoreS3Cache should create S3-based RestoreCache") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    restoreS3Cache("my-bucket", "us-east-1", "libs", "lib/")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.RestoreCache(cache, paths) =>
        cache match
          case s3Cache: S3Cache =>
            assertEquals(s3Cache.bucket, "my-bucket")
            assertEquals(s3Cache.region, "us-east-1")
            assertEquals(s3Cache.key.value, "libs")
            assertEquals(s3Cache.scope, CacheScope.Global)
            assertEquals(paths.toList, List("lib/"))
          case other => fail(s"Expected S3Cache, but got ${other.toString}")
      case other => fail(s"Expected StepLike.RestoreCache, but got ${other.toString}")
  }

  test("DSL saveGCSCache should create GCS-based SaveCache") {
    import dsl.*
    given StepsBuilder = new StepsBuilder()
    saveGCSCache("my-gcs-bucket", "artifacts", "dist/")

    val result = summon[StepsBuilder].result
    assertEquals(result.size, 1)
    result.head match
      case StepLike.SaveCache(cache, paths) =>
        cache match
          case gcsCache: GCSCache =>
            assertEquals(gcsCache.bucket, "my-gcs-bucket")
            assertEquals(gcsCache.key.value, "artifacts")
            assertEquals(gcsCache.scope, CacheScope.Global)
            assertEquals(paths.toList, List("dist/"))
          case other => fail(s"Expected GCSCache, but got ${other.toString}")
      case other => fail(s"Expected StepLike.SaveCache, but got ${other.toString}")
  }
