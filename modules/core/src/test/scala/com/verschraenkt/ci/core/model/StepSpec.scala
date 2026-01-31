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

import cats.data.{ NonEmptyList, NonEmptyVector }
import munit.FunSuite

import scala.concurrent.duration.DurationInt

class StepSpec extends FunSuite:

  // StepMeta tests
  test("StepMeta creation with default values") {
    val meta = StepMeta()
    assertEquals(meta.id, None)
    assertEquals(meta.when, When.Always)
    assertEquals(meta.timeout, None)
    assertEquals(meta.continueOnError, false)
    assertEquals(meta.retry, None)
    assertEquals(meta.env, Map.empty)
    assertEquals(meta.workingDirectory, None)
  }

  test("StepMeta creation with all parameters") {
    val meta = StepMeta(
      id = Some("step-1"),
      when = When.Always,
      timeout = Some(3.minutes),
      continueOnError = true,
      retry = Some(Retry(3, 3.minutes, RetryMode.Linear)),
      env = Map("NODE_ENV" -> "production", "DEBUG" -> "true"),
      workingDirectory = Some("/app")
    )
    assertEquals(meta.id, Some("step-1"))
    assertEquals(meta.when, When.Always)
    assertEquals(meta.timeout, Some(3.minutes))
    assertEquals(meta.continueOnError, true)
    assertEquals(meta.retry, Some(Retry(3, 3.minutes, RetryMode.Linear)))
    assertEquals(meta.env.size, 2)
    assertEquals(meta.workingDirectory, Some("/app"))
  }

  // Step.Checkout tests
  test("Checkout step creation") {
    given StepMeta = StepMeta()
    val checkout   = Step.Checkout()
    assert(checkout.isInstanceOf[Step.Checkout])
    assert(checkout.isInstanceOf[HasMeta])
    assertEquals(checkout.meta, summon[StepMeta])
  }

  test("Checkout step with custom meta") {
    given StepMeta = StepMeta(
      id = Some("checkout-code"),
      timeout = Some(60.minutes)
    )
    val checkout = Step.Checkout()
    assertEquals(checkout.meta.id, Some("checkout-code"))
    assertEquals(checkout.meta.timeout, Some(60.minutes))
  }

  // Step.Run tests
  test("Run step creation with Exec command") {
    given StepMeta = StepMeta()
    val command    = Command.Exec("npm", List("install"))
    val run        = Step.Run(command)
    assert(run.isInstanceOf[Step.Run])
    assertEquals(run.command, command)
  }

  test("Run step creation with Shell command") {
    given StepMeta = StepMeta(id = Some("build"))
    val command    = Command.Shell("npm run build")
    val run        = Step.Run(command)
    assertEquals(run.command, command)
    assertEquals(run.meta.id, Some("build"))
  }

  test("Run step with custom meta and environment") {
    given StepMeta = StepMeta(
      env = Map("CI" -> "true"),
      workingDirectory = Some("/workspace")
    )
    val command = Command.Exec("make", List("test"))
    val run     = Step.Run(command)
    assertEquals(run.meta.env, Map("CI" -> "true"))
    assertEquals(run.meta.workingDirectory, Some("/workspace"))
  }

  // Step.RestoreCache tests
  test("RestoreCache step creation") {
    given StepMeta = StepMeta()
    val key        = CacheKey.literal("deps")
    val cache      = Cache.RestoreCache(key, NonEmptyList.of("node_modules"))
    val paths      = NonEmptyList.of("node_modules")
    val restore    = Step.RestoreCache(cache, paths)

    assert(restore.isInstanceOf[Step.RestoreCache])
    assertEquals(restore.cache, cache)
    assertEquals(restore.paths.toList, List("node_modules"))
  }

  test("RestoreCache step with multiple paths") {
    given StepMeta = StepMeta(id = Some("restore-deps"))
    val key        = CacheKey.literal("build-cache")
    val cache      = Cache.RestoreCache(key, NonEmptyList.of("target", ".ivy2"))
    val paths      = NonEmptyList.of("target", ".ivy2", ".sbt")
    val restore    = Step.RestoreCache(cache, paths)

    assertEquals(restore.paths.toList.length, 3)
    assertEquals(restore.meta.id, Some("restore-deps"))
  }

  // Step.SaveCache tests
  test("SaveCache step creation") {
    given StepMeta = StepMeta()
    val key        = CacheKey.literal("deps")
    val cache      = Cache.SaveCache(key, NonEmptyList.of("node_modules"))
    val paths      = NonEmptyList.of("node_modules")
    val save       = Step.SaveCache(cache, paths)

    assert(save.isInstanceOf[Step.SaveCache])
    assertEquals(save.cache, cache)
    assertEquals(save.paths.toList, List("node_modules"))
  }

  test("SaveCache step with branch scope") {
    given StepMeta = StepMeta(when = When.OnSuccess)
    val key        = CacheKey.literal("build")
    val cache      = Cache.SaveCache(key, NonEmptyList.of("target"), CacheScope.Branch)
    val paths      = NonEmptyList.of("target")
    val save       = Step.SaveCache(cache, paths)

    assertEquals(save.cache.scope, CacheScope.Branch)
    assertEquals(save.meta.when, When.OnSuccess)
  }

  // Step.Composite tests
  test("Composite step creation") {
    given StepMeta                = StepMeta()
    val checkout                  = Step.Checkout()
    val run                       = Step.Run(Command.Exec("npm", List("install")))
    val composite: Step.Composite = Step.Composite(NonEmptyVector.of(checkout, run))

    assert(composite.isInstanceOf[Step.Composite])
    assertEquals(composite.steps.length, 2)
    assertEquals(composite.steps.head, checkout)
  }

  test("Composite step with multiple steps") {
    given meta1: StepMeta = StepMeta(id = Some("step1"))
    val checkout          = Step.Checkout()(using meta1)

    given meta2: StepMeta = StepMeta(id = Some("step2"))
    val install           = Step.Run(Command.Exec("npm", List("install")))(using meta2)

    given meta3: StepMeta = StepMeta(id = Some("step3"))
    val test              = Step.Run(Command.Shell("npm test"))(using meta3)

    val composite = Step.Composite(NonEmptyVector.of(checkout, install, test))
    assertEquals(composite.asInstanceOf[Step.Composite].steps.length, 3)
  }

  // getMeta extension tests
  test("getMeta returns Some for steps with metadata") {
    given StepMeta = StepMeta(id = Some("test-step"))
    val checkout   = Step.Checkout()
    assertEquals(checkout.getMeta, Some(summon[StepMeta]))
  }

  test("getMeta returns None for Composite steps") {
    given StepMeta = StepMeta()
    val composite  = Step.Composite(NonEmptyVector.one(Step.Checkout()))
    assertEquals(composite.getMeta, None)
  }

  test("getMeta returns metadata for Run step") {
    given StepMeta = StepMeta(retry = Some(Retry(3, 3.minutes, RetryMode.Linear)))
    val run        = Step.Run(Command.Exec("echo"))
    assertEquals(run.getMeta.get.retry, Some(Retry(3, 3.minutes, RetryMode.Linear)))
  }

  test("getMeta returns metadata for RestoreCache step") {
    given StepMeta = StepMeta(timeout = Some(120.minutes))
    val key        = CacheKey.literal("test")
    val cache      = Cache.RestoreCache(key, NonEmptyList.of("/cache"))
    val restore    = Step.RestoreCache(cache, NonEmptyList.of("/cache"))
    assertEquals(restore.getMeta.get.timeout, Some(120.minutes))
  }

  test("getMeta returns metadata for SaveCache step") {
    given StepMeta = StepMeta(continueOnError = true)
    val key        = CacheKey.literal("test")
    val cache      = Cache.SaveCache(key, NonEmptyList.of("/cache"))
    val save       = Step.SaveCache(cache, NonEmptyList.of("/cache"))
    assertEquals(save.getMeta.get.continueOnError, true)
  }

  // withMeta extension tests
  test("withMeta modifies Checkout step metadata") {
    given StepMeta = StepMeta(id = Some("original"))
    val checkout   = Step.Checkout()
    val modified   = checkout.withMeta(m => m.copy(id = Some("modified")))

    modified match
      case c: Step.Checkout => assertEquals(c.meta.id, Some("modified"))
      case _                => fail("Expected Checkout step")
  }

  test("withMeta modifies Run step metadata") {
    given StepMeta = StepMeta(timeout = Some(60.minutes))
    val run        = Step.Run(Command.Exec("echo"))
    val modified   = run.withMeta(m => m.copy(timeout = Some(120.minutes)))

    modified match
      case r: Step.Run => assertEquals(r.meta.timeout, Some(120.minutes))
      case _           => fail("Expected Run step")
  }

  test("withMeta modifies RestoreCache step metadata") {
    given StepMeta = StepMeta(retry = Some(Retry(3, 3.minutes, RetryMode.Linear)))
    val key        = CacheKey.literal("test")
    val cache      = Cache.RestoreCache(key, NonEmptyList.of("/cache"))
    val restore    = Step.RestoreCache(cache, NonEmptyList.of("/cache"))
    val modified   = restore.withMeta(m => m.copy(retry = Some(Retry(3, 3.minutes, RetryMode.Linear))))

    modified match
      case rc: Step.RestoreCache => assertEquals(rc.meta.retry, Some(Retry(3, 3.minutes, RetryMode.Linear)))
      case _                     => fail("Expected RestoreCache step")
  }

  test("withMeta modifies SaveCache step metadata") {
    given StepMeta = StepMeta(continueOnError = false)
    val key        = CacheKey.literal("test")
    val cache      = Cache.SaveCache(key, NonEmptyList.of("/cache"))
    val save       = Step.SaveCache(cache, NonEmptyList.of("/cache"))
    val modified   = save.withMeta(m => m.copy(continueOnError = true))

    modified match
      case sc: Step.SaveCache => assertEquals(sc.meta.continueOnError, true)
      case _                  => fail("Expected SaveCache step")
  }

  test("withMeta returns same Composite step unchanged") {
    given StepMeta = StepMeta()
    val composite  = Step.Composite(NonEmptyVector.one(Step.Checkout()))
    val modified   = composite.withMeta(m => m.copy(id = Some("ignored")))

    modified match
      case c: Step.Composite => assertEquals(c, composite)
      case _                 => fail("Expected Composite step")
  }

  test("withMeta can modify multiple metadata fields") {
    given StepMeta = StepMeta(id = Some("original"), timeout = Some(60.minutes))
    val checkout   = Step.Checkout()
    val modified = checkout.withMeta(m =>
      m.copy(
        id = Some("updated"),
        timeout = Some(120.minutes),
        continueOnError = true,
        retry = Some(Retry(3, 3.minutes, RetryMode.Linear))
      )
    )

    modified match
      case c: Step.Checkout =>
        assertEquals(c.meta.id, Some("updated"))
        assertEquals(c.meta.timeout, Some(120.minutes))
        assertEquals(c.meta.continueOnError, true)
        assertEquals(c.meta.retry, Some(Retry(3, 3.minutes, RetryMode.Linear)))
      case _ => fail("Expected Checkout step")
  }

  test("withMeta preserves command in Run step") {
    given StepMeta = StepMeta()
    val command    = Command.Exec("npm", List("test"))
    val run        = Step.Run(command)
    val modified   = run.withMeta(m => m.copy(id = Some("test-step")))

    modified match
      case r: Step.Run =>
        assertEquals(r.command, command)
        assertEquals(r.meta.id, Some("test-step"))
      case _ => fail("Expected Run step")
  }

  test("withMeta preserves cache and paths in RestoreCache step") {
    given StepMeta = StepMeta()
    val key        = CacheKey.literal("build")
    val cache      = Cache.RestoreCache(key, NonEmptyList.of("target"))
    val paths      = NonEmptyList.of("target", ".ivy2")
    val restore    = Step.RestoreCache(cache, paths)
    val modified   = restore.withMeta(m => m.copy(id = Some("restore-step")))

    modified match
      case rc: Step.RestoreCache =>
        assertEquals(rc.cache, cache)
        assertEquals(rc.paths, paths)
        assertEquals(rc.meta.id, Some("restore-step"))
      case _ => fail("Expected RestoreCache step")
  }

  test("withMeta preserves cache and paths in SaveCache step") {
    given StepMeta = StepMeta()
    val key        = CacheKey.literal("build")
    val cache      = Cache.SaveCache(key, NonEmptyList.of("target"))
    val paths      = NonEmptyList.of("target", ".sbt")
    val save       = Step.SaveCache(cache, paths)
    val modified   = save.withMeta(m => m.copy(id = Some("save-step")))

    modified match
      case sc: Step.SaveCache =>
        assertEquals(sc.cache, cache)
        assertEquals(sc.paths, paths)
        assertEquals(sc.meta.id, Some("save-step"))
      case _ => fail("Expected SaveCache step")
  }

  // ~> operator tests
  test("~> operator chains two steps") {
    given StepMeta = StepMeta()
    val checkout   = Step.Checkout()
    val run        = Step.Run(Command.Exec("npm", List("install")))
    val composite  = checkout ~> run

    assertEquals(composite.steps.length, 2)
    assertEquals(composite.steps.head, checkout)
    assertEquals(composite.steps.tail.head, run)
  }

  test("~> operator chains composite with step") {
    given meta1: StepMeta = StepMeta()
    val checkout          = Step.Checkout()(using meta1)

    given meta2: StepMeta = StepMeta()
    val install           = Step.Run(Command.Exec("npm", List("install")))(using meta2)

    given meta3: StepMeta = StepMeta()
    val test              = Step.Run(Command.Shell("npm test"))(using meta3)

    val composite = checkout ~> install
    val extended  = composite ~> test

    assertEquals(extended.steps.length, 3)
    assertEquals(extended.steps.toVector, Vector(checkout, install, test))
  }

  test("~> operator builds complex step chain") {
    given meta1: StepMeta = StepMeta(id = Some("checkout"))
    val checkout          = Step.Checkout()(using meta1)

    given meta2: StepMeta = StepMeta(id = Some("restore"))
    val key               = CacheKey.literal("deps")
    val cache             = Cache.RestoreCache(key, NonEmptyList.of("node_modules"))
    val restore           = Step.RestoreCache(cache, NonEmptyList.of("node_modules"))(using meta2)

    given meta3: StepMeta = StepMeta(id = Some("install"))
    val install           = Step.Run(Command.Exec("npm", List("install")))(using meta3)

    given meta4: StepMeta = StepMeta(id = Some("test"))
    val test              = Step.Run(Command.Shell("npm test"))(using meta4)

    given meta5: StepMeta = StepMeta(id = Some("save"))
    val saveCache         = Cache.SaveCache(key, NonEmptyList.of("node_modules"))
    val save              = Step.SaveCache(saveCache, NonEmptyList.of("node_modules"))(using meta5)

    val pipeline: Step.Composite = checkout ~> restore ~> install ~> test ~> save
    val steps                    = pipeline.steps.toVector

    assertEquals(pipeline.steps.length, 5)
    assertEquals(steps(0), checkout)
    assertEquals(steps(1), restore)
    assertEquals(steps(2), install)
    assertEquals(steps(3), test)
    assertEquals(steps(4), save)
  }

  test("~> operator preserves step metadata in chain") {
    given meta1: StepMeta = StepMeta(id = Some("step1"), retry = Some(Retry(3, 3.minutes, RetryMode.Linear)))
    val step1             = Step.Checkout()(using meta1)

    given meta2: StepMeta = StepMeta(id = Some("step2"), timeout = Some(300.minutes))
    val step2             = Step.Run(Command.Exec("echo"))(using meta2)

    val composite = step1 ~> step2

    composite.steps.head match
      case c: Step.Checkout =>
        assertEquals(c.meta.id, Some("step1"))
        assertEquals(c.meta.retry, Some(Retry(3, 3.minutes, RetryMode.Linear)))
      case _ => fail("Expected Checkout step")

    composite.steps.tail.head match
      case r: Step.Run =>
        assertEquals(r.meta.id, Some("step2"))
        assertEquals(r.meta.timeout, Some(300.minutes))
      case _ => fail("Expected Run step")
  }

  // Integration tests
  test("complete CI pipeline with all step types") {
    given checkoutMeta: StepMeta = StepMeta(id = Some("checkout"), timeout = Some(60.minutes))
    val checkout                 = Step.Checkout()(using checkoutMeta)

    given restoreMeta: StepMeta = StepMeta(id = Some("restore-cache"))
    val key                     = CacheKey.literal("build-deps")
    val restoreCache            = Cache.restoreForBranch(key, NonEmptyList.of("target", ".ivy2"), "main")
    val restore = Step.RestoreCache(restoreCache, NonEmptyList.of("target", ".ivy2"))(using restoreMeta)

    given compileMeta: StepMeta = StepMeta(
      id = Some("compile"),
      env = Map("SBT_OPTS" -> "-Xmx2G"),
      timeout = Some(600.minutes)
    )
    val compile = Step.Run(Command.Exec("sbt", List("compile")))(using compileMeta)

    given testMeta: StepMeta = StepMeta(
      id = Some("test"),
      continueOnError = true,
      retry = Some(Retry(3, 3.minutes, RetryMode.Linear))
    )
    val test = Step.Run(Command.Shell("sbt test"))(using testMeta)

    given saveMeta: StepMeta = StepMeta(id = Some("save-cache"), when = When.OnSuccess)
    val saveCache            = Cache.saveForBranch(key, NonEmptyList.of("target", ".ivy2"), "main")
    val save                 = Step.SaveCache(saveCache, NonEmptyList.of("target", ".ivy2"))(using saveMeta)

    val pipeline: Step.Composite = checkout ~> restore ~> compile ~> test ~> save
    val steps                    = pipeline.steps.toVector
    assertEquals(pipeline.steps.length, 5)

    // Verify each step maintains its properties
    steps(0).getMeta.get.id match
      case Some("checkout") => ()
      case _                => fail("Wrong checkout id")

    steps(2).getMeta.get.env match
      case env if env.contains("SBT_OPTS") => ()
      case _                               => fail("Compile env not preserved")

    steps(3).getMeta.get.retry match
      case Some(Retry(3, _, RetryMode.Linear)) => ()
      case _                                   => fail("Test retry not preserved")
  }

  test("nested composite steps") {
    given meta: StepMeta = StepMeta()
    val step1            = Step.Checkout()
    val step2            = Step.Run(Command.Exec("echo", List("1")))
    val inner            = step1 ~> step2

    val step3 = Step.Run(Command.Exec("echo", List("2")))
    val outer = inner ~> step3

    assertEquals(outer.steps.length, 3)
  }

  // Tests for Issue 2.1: findAllCommands with composite steps
  test("StepUtils.findAllCommands extracts commands from composite steps") {
    given StepMeta = StepMeta()
    val cmd1       = Command.Shell("echo hello")
    val cmd2       = Command.Exec("npm", List("install"))
    val cmd3       = Command.Shell("npm test")

    val composite = Step.Composite(
      cats.data.NonEmptyVector.of(
        Step.Run(cmd1),
        Step.Run(cmd2),
        Step.Checkout(),
        Step.Run(cmd3)
      )
    )

    val commands = StepUtils.findAllCommands(composite)
    assertEquals(commands.length, 3)
    assertEquals(commands(0), cmd1)
    assertEquals(commands(1), cmd2)
    assertEquals(commands(2), cmd3)
  }

  test("StepUtils.findAllCommands handles nested composite steps") {
    given StepMeta = StepMeta()
    val cmd1       = Command.Shell("echo 1")
    val cmd2       = Command.Shell("echo 2")
    val cmd3       = Command.Shell("echo 3")

    val inner = Step.Composite(
      cats.data.NonEmptyVector.of(
        Step.Run(cmd1),
        Step.Run(cmd2)
      )
    )
    val outer = Step.Composite(
      cats.data.NonEmptyVector.of(
        inner,
        Step.Run(cmd3)
      )
    )

    val commands = StepUtils.findAllCommands(outer)
    assertEquals(commands.length, 3)
  }

  test("StepUtils.findAllShellCommands works with composite steps") {
    given StepMeta = StepMeta()
    val composite = Step.Composite(
      cats.data.NonEmptyVector.of(
        Step.Run(Command.Shell("echo hello")),
        Step.Run(Command.Exec("npm", List("install"))),
        Step.Run(Command.Shell("npm test"))
      )
    )

    val shellCommands = StepUtils.findAllShellCommands(composite)
    assertEquals(shellCommands.length, 2)
    assertEquals(shellCommands(0), "echo hello")
    assertEquals(shellCommands(1), "npm test")
  }

  test("StepUtils.findAllExecCommands works with composite steps") {
    given StepMeta = StepMeta()
    val composite = Step.Composite(
      cats.data.NonEmptyVector.of(
        Step.Run(Command.Shell("echo hello")),
        Step.Run(Command.Exec("npm", List("install"))),
        Step.Run(Command.Exec("sbt", List("compile", "test")))
      )
    )

    val execCommands = StepUtils.findAllExecCommands(composite)
    assertEquals(execCommands.length, 2)
    assertEquals(execCommands(0), ("npm", List("install")))
    assertEquals(execCommands(1), ("sbt", List("compile", "test")))
  }
