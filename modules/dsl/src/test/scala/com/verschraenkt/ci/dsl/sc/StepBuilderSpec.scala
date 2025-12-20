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
      case other => fail(s"Expected Step.Run, but got $other")
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
      case other => fail(s"Expected Step.Run, but got $other")
  }

  test("StepBuilder should convert StepLike.Run to Step.Run with custom shell") {
    given StepsBuilder = new StepsBuilder()
    val runStepLike    = StepLike.Run("pwsh -Command 'Write-Host \"Hello\"'", ShellKind.Pwsh)
    val step           = StepBuilder.toStep(runStepLike)

    step match
      case Step.Run(Command.Shell(script, shell, _, _, _)) =>
        assertEquals(script, "pwsh -Command 'Write-Host \"Hello\"'")
        assertEquals(shell, ShellKind.Pwsh)
      case other => fail(s"Expected Step.Run, but got $other")
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

