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
package com.verschraenkt.ci.storage.db.codecs

import cats.data.{ NonEmptyList, NonEmptyVector }
import com.verschraenkt.ci.core.model.*
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{ Decoder, Encoder, Json }
import munit.FunSuite

import scala.concurrent.duration.*

class JsonCodecsSpec extends FunSuite:
  import JsonCodecs.given

  // Helper to test round-trip encoding/decoding
  def testRoundTrip[A](name: String, value: A)(using enc: Encoder[A], dec: Decoder[A]): Unit =
    test(s"$name - round-trip encoding/decoding") {
      val json    = value.asJson
      val decoded = json.as[A]
      decoded match
        case Right(result) => assertEquals(result, value)
        case Left(error)   => fail("Failed to decode:" + error.toString)
    }

  // Test Value Class Encoders/Decoders

  test("PipelineId encoding") {
    val id   = PipelineId("pipeline-123")
    val json = id.asJson
    assertEquals(json, Json.fromString("pipeline-123"))
  }

  test("PipelineId decoding") {
    val json    = Json.fromString("pipeline-456")
    val decoded = json.as[PipelineId]
    assertEquals(decoded, Right(PipelineId("pipeline-456")))
  }

  testRoundTrip("PipelineId", PipelineId("test-pipeline"))

  test("WorkflowId encoding") {
    val id   = WorkflowId("workflow-123")
    val json = id.asJson
    assertEquals(json, Json.fromString("workflow-123"))
  }

  test("WorkflowId decoding") {
    val json    = Json.fromString("workflow-456")
    val decoded = json.as[WorkflowId]
    assertEquals(decoded, Right(WorkflowId("workflow-456")))
  }

  testRoundTrip("WorkflowId", WorkflowId("test-workflow"))

  test("JobId encoding") {
    val id   = JobId("job-123")
    val json = id.asJson
    assertEquals(json, Json.fromString("job-123"))
  }

  test("JobId decoding") {
    val json    = Json.fromString("job-456")
    val decoded = json.as[JobId]
    assertEquals(decoded, Right(JobId("job-456")))
  }

  testRoundTrip("JobId", JobId("test-job"))

  test("StepId encoding") {
    val id   = StepId("step-123")
    val json = id.asJson
    assertEquals(json, Json.fromString("step-123"))
  }

  test("StepId decoding") {
    val json    = Json.fromString("step-456")
    val decoded = json.as[StepId]
    assertEquals(decoded, Right(StepId("step-456")))
  }

  testRoundTrip("StepId", StepId("test-step"))

  test("CacheKey encoding") {
    val key  = CacheKey.literal("cache-key-123")
    val json = key.asJson
    assertEquals(json, Json.fromString("cache-key-123"))
  }

  test("CacheKey decoding") {
    val json    = Json.fromString("cache-key-456")
    val decoded = json.as[CacheKey]
    assertEquals(decoded, Right(CacheKey.literal("cache-key-456")))
  }

  testRoundTrip("CacheKey", CacheKey.literal("test-cache-key"))

  // Test FiniteDuration Encoder/Decoder

  test("FiniteDuration encoding") {
    val duration = 5000.millis
    val json     = duration.asJson
    assertEquals(json, Json.fromLong(5000L))
  }

  test("FiniteDuration decoding") {
    val json    = Json.fromLong(3000L)
    val decoded = json.as[FiniteDuration]
    assertEquals(decoded, Right(3000.millis))
  }

  testRoundTrip("FiniteDuration", 10.seconds)

  // Test Cats Data Structures Encoders/Decoders

  test("NonEmptyVector encoding") {
    val nev  = NonEmptyVector.of(1, 2, 3)
    val json = nev.asJson
    assertEquals(json, Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3)))
  }

  test("NonEmptyVector decoding success") {
    val json    = Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val decoded = json.as[NonEmptyVector[Int]]
    assertEquals(decoded, Right(NonEmptyVector.of(1, 2, 3)))
  }

  test("NonEmptyVector decoding empty vector fails") {
    val json    = Json.arr()
    val decoded = json.as[NonEmptyVector[Int]]
    assert(decoded.isLeft)
  }

  testRoundTrip("NonEmptyVector[String]", NonEmptyVector.of("a", "b", "c"))

  test("NonEmptyList encoding") {
    val nel  = NonEmptyList.of("x", "y", "z")
    val json = nel.asJson
    assertEquals(json, Json.arr(Json.fromString("x"), Json.fromString("y"), Json.fromString("z")))
  }

  test("NonEmptyList decoding success") {
    val json    = Json.arr(Json.fromString("x"), Json.fromString("y"))
    val decoded = json.as[NonEmptyList[String]]
    assertEquals(decoded, Right(NonEmptyList.of("x", "y")))
  }

  test("NonEmptyList decoding empty list fails") {
    val json    = Json.arr()
    val decoded = json.as[NonEmptyList[String]]
    assert(decoded.isLeft)
  }

  testRoundTrip("NonEmptyList[Int]", NonEmptyList.of(10, 20, 30))

  // Test Enum Encoders/Decoders

  test("CacheScope encoding") {
    val scope = CacheScope.Job
    val json  = scope.asJson
    assertEquals(json, Json.fromInt(CacheScope.Job.intVal))
  }

  test("CacheScope decoding success") {
    val json    = Json.fromInt(CacheScope.Pipeline.intVal)
    val decoded = json.as[CacheScope]
    assertEquals(decoded, Right(CacheScope.Pipeline))
  }

  test("CacheScope decoding invalid value fails") {
    val json    = Json.fromInt(999)
    val decoded = json.as[CacheScope]
    assert(decoded.isLeft)
  }

  testRoundTrip("CacheScope.Job", CacheScope.Job)
  testRoundTrip("CacheScope.Branch", CacheScope.Branch)
  testRoundTrip("CacheScope.Pipeline", CacheScope.Pipeline)

  test("PatternKind encoding") {
    assertEquals(PatternKind.Wildcard.asJson, Json.fromString("Wildcard"))
    assertEquals(PatternKind.Regex.asJson, Json.fromString("Regex"))
    assertEquals(PatternKind.Exact.asJson, Json.fromString("Exact"))
  }

  test("PatternKind decoding success") {
    assertEquals(Json.fromString("Wildcard").as[PatternKind], Right(PatternKind.Wildcard))
    assertEquals(Json.fromString("Regex").as[PatternKind], Right(PatternKind.Regex))
    assertEquals(Json.fromString("Exact").as[PatternKind], Right(PatternKind.Exact))
  }

  test("PatternKind decoding invalid value fails") {
    val decoded = Json.fromString("Invalid").as[PatternKind]
    assert(decoded.isLeft)
  }

  testRoundTrip("PatternKind.Wildcard", PatternKind.Wildcard)
  testRoundTrip("PatternKind.Regex", PatternKind.Regex)
  testRoundTrip("PatternKind.Exact", PatternKind.Exact)

  test("ShellKind encoding") {
    assertEquals(ShellKind.Bash.asJson, Json.fromString("Bash"))
    assertEquals(ShellKind.Sh.asJson, Json.fromString("Sh"))
    assertEquals(ShellKind.Pwsh.asJson, Json.fromString("Pwsh"))
    assertEquals(ShellKind.Cmd.asJson, Json.fromString("Cmd"))
  }

  test("ShellKind decoding success") {
    assertEquals(Json.fromString("Bash").as[ShellKind], Right(ShellKind.Bash))
    assertEquals(Json.fromString("Sh").as[ShellKind], Right(ShellKind.Sh))
    assertEquals(Json.fromString("Pwsh").as[ShellKind], Right(ShellKind.Pwsh))
    assertEquals(Json.fromString("Cmd").as[ShellKind], Right(ShellKind.Cmd))
  }

  test("ShellKind decoding invalid value fails") {
    val decoded = Json.fromString("Invalid").as[ShellKind]
    assert(decoded.isLeft)
  }

  testRoundTrip("ShellKind.Bash", ShellKind.Bash)
  testRoundTrip("ShellKind.Pwsh", ShellKind.Pwsh)

  test("When encoding") {
    assertEquals(When.Always.asJson, Json.fromString("Always"))
    assertEquals(When.OnSuccess.asJson, Json.fromString("OnSuccess"))
    assertEquals(When.OnFailure.asJson, Json.fromString("OnFailure"))
  }

  test("When decoding success") {
    assertEquals(Json.fromString("Always").as[When], Right(When.Always))
    assertEquals(Json.fromString("OnSuccess").as[When], Right(When.OnSuccess))
    assertEquals(Json.fromString("OnFailure").as[When], Right(When.OnFailure))
  }

  test("When decoding invalid value fails") {
    val decoded = Json.fromString("Invalid").as[When]
    assert(decoded.isLeft)
  }

  testRoundTrip("When.Always", When.Always)
  testRoundTrip("When.OnSuccess", When.OnSuccess)
  testRoundTrip("When.OnFailure", When.OnFailure)

  test("RetryMode encoding") {
    assertEquals(RetryMode.Linear.asJson, Json.fromString("Linear"))
    assertEquals(RetryMode.Exponential.asJson, Json.fromString("Exponential"))
  }

  test("RetryMode decoding success") {
    assertEquals(Json.fromString("Linear").as[RetryMode], Right(RetryMode.Linear))
    assertEquals(Json.fromString("Exponential").as[RetryMode], Right(RetryMode.Exponential))
  }

  test("RetryMode decoding invalid value fails") {
    val decoded = Json.fromString("Invalid").as[RetryMode]
    assert(decoded.isLeft)
  }

  testRoundTrip("RetryMode.Linear", RetryMode.Linear)
  testRoundTrip("RetryMode.Exponential", RetryMode.Exponential)

  // Test Simple Case Class Encoders/Decoders

  test("Container encoding/decoding") {
    val container = Container(
      image = "ubuntu:22.04"
    )
    testRoundTrip("Container", container)
  }

  test("Resource encoding/decoding") {
    val resource = Resource(
      2,
      4096,
      diskMiB = 10000
    )
    testRoundTrip("Resource", resource)
  }

  test("Retry encoding/decoding") {
    val retry = Retry(
      3,
      5.minutes,
      mode = RetryMode.Exponential
    )
    testRoundTrip("Retry", retry)
  }

  test("Policy encoding/decoding") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 600,
      denyPatterns = List("rm -rf", "format c:")
    )
    testRoundTrip("Policy", policy)
  }

  test("CommandResult encoding/decoding") {
    val result = CommandResult(
      exitCode = 0,
      stdout = Vector("Line 1", "Line 2"),
      stderr = Vector.empty,
      startedAt = 1000L,
      endedAt = 2000L
    )
    testRoundTrip("CommandResult", result)
  }

  // Test Cache Encoders/Decoders

  test("Cache.RestoreCache encoding/decoding") {
    val cache = Cache.RestoreCache(
      key = CacheKey.literal("my-cache"),
      paths = NonEmptyList.of("/cache/path1", "/cache/path2"),
      scope = CacheScope.Pipeline
    )
    testRoundTrip("Cache.RestoreCache", cache)
  }

  test("Cache.SaveCache encoding/decoding") {
    val cache = Cache.SaveCache(
      key = CacheKey.literal("my-cache"),
      paths = NonEmptyList.of("/cache/path1"),
      scope = CacheScope.Job
    )
    testRoundTrip("Cache.SaveCache", cache)
  }

  // Test Command Encoders/Decoders (ADT)

  test("Command.Exec encoding") {
    val cmd = Command.Exec(
      program = "npm",
      args = List("install", "--production"),
      env = Map("NODE_ENV" -> "production"),
      cwd = Some("/app"),
      timeoutSec = Some(300)
    )
    val json = cmd.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("Exec"))
    assertEquals(json.hcursor.downField("program").as[String], Right("npm"))
    assertEquals(json.hcursor.downField("args").as[List[String]], Right(List("install", "--production")))
  }

  test("Command.Exec decoding") {
    val json = parse("""
    {
      "type": "Exec",
      "program": "echo",
      "args": ["hello", "world"],
      "env": {"VAR": "value"},
      "cwd": "/tmp",
      "timeoutSec": 60
    }
    """).toOption.get

    val decoded = json.as[Command]
    decoded match
      case Right(Command.Exec(program, args, env, cwd, timeoutSec)) =>
        assertEquals(program, "echo")
        assertEquals(args, List("hello", "world"))
        assertEquals(env, Map("VAR" -> "value"))
        assertEquals(cwd, Some("/tmp"))
        assertEquals(timeoutSec, Some(60))
      case _ => fail("Expected Command.Exec")
  }

  testRoundTrip("Command.Exec", Command.Exec("ls", List("-la")))

  test("Command.Shell encoding") {
    val cmd = Command.Shell(
      script = "npm test && npm run build",
      shell = ShellKind.Bash,
      env = Map("CI" -> "true"),
      cwd = Some("/project"),
      timeoutSec = Some(600)
    )
    val json = cmd.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("Shell"))
    assertEquals(json.hcursor.downField("script").as[String], Right("npm test && npm run build"))
    assertEquals(json.hcursor.downField("shell").as[ShellKind], Right(ShellKind.Bash))
  }

  test("Command.Shell decoding") {
    val json = parse("""
    {
      "type": "Shell",
      "script": "echo 'hello'",
      "shell": "Bash",
      "env": {},
      "cwd": null,
      "timeoutSec": null
    }
    """).toOption.get

    val decoded = json.as[Command]
    decoded match
      case Right(Command.Shell(script, shell, env, cwd, timeoutSec)) =>
        assertEquals(script, "echo 'hello'")
        assertEquals(shell, ShellKind.Bash)
        assertEquals(env, Map.empty[String, String])
        assertEquals(cwd, None)
        assertEquals(timeoutSec, None)
      case _ => fail("Expected Command.Shell")
  }

  testRoundTrip("Command.Shell", Command.Shell("ls -la", ShellKind.Bash))

  test("Command.Composite encoding") {
    val cmd1      = Command.Exec("echo", List("hello"))
    val cmd2      = Command.Shell("pwd")
    val composite = Command.Composite(NonEmptyVector.of(cmd1, cmd2))

    val json = composite.asJson
    assertEquals(json.hcursor.downField("type").as[String], Right("Composite"))
  }

  test("Command.Composite decoding") {
    val json = parse("""
    {
      "type": "Composite",
      "steps": [
        {
          "type": "Exec",
          "program": "echo",
          "args": ["hello"],
          "env": {},
          "cwd": null,
          "timeoutSec": null
        },
        {
          "type": "Shell",
          "script": "pwd",
          "shell": "Bash",
          "env": {},
          "cwd": null,
          "timeoutSec": null
        }
      ]
    }
    """).toOption.get

    val decoded = json.as[Command]
    decoded match
      case Right(Command.Composite(steps)) =>
        assertEquals(steps.length, 2)
      case _ => fail("Expected Command.Composite")
  }

  testRoundTrip(
    "Command.Composite",
    Command.Composite(
      NonEmptyVector.of(
        Command.Exec("echo", List("test")),
        Command.Shell("ls")
      )
    )
  )

  // Test Step Encoders/Decoders (ADT)

  test("Step.Checkout encoding/decoding") {
    given StepMeta = StepMeta(
      id = Some("checkout-1"),
      when = When.Never,
      timeout = None
    )

    val step: Step = Step.Checkout()
    val json = step.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("Checkout"))

    val decoded = json.as[Step]
    assert(decoded.isRight)
    decoded match
      case Right(Step.Checkout()) => ()
      case _                      => fail("Expected Step.Checkout")
  }

  test("Step.Run encoding/decoding") {
    given StepMeta = StepMeta(
      id = Some("run-1"),
      timeout = None
    )

    val cmd  = Command.Exec("npm", List("test"))
    val step: Step = Step.Run(cmd)
    val json = step.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("Run"))

    val decoded = json.as[Step]
    assert(decoded.isRight)
  }

  test("Step.RestoreCache encoding/decoding") {
    given StepMeta = StepMeta(
      id = Some("restore-1"),
      timeout = None
    )

    val cache = Cache.RestoreCache(
      key = CacheKey.literal("npm-cache"),
      paths = NonEmptyList.of("/node_modules"),
      scope = CacheScope.Pipeline
    )
    val step: Step = Step.RestoreCache(cache, NonEmptyList.of("/node_modules"))
    val json = step.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("RestoreCache"))

    val decoded = json.as[Step]
    assert(decoded.isRight)
  }

  test("Step.SaveCache encoding/decoding") {
    given StepMeta = StepMeta(
      id = Some("Save cache"),
      continueOnError = true,
      when= When.OnSuccess,
      timeout = None
    )

    val cache = Cache.SaveCache(
      key = CacheKey.literal("npm-cache"),
      paths = NonEmptyList.of("/node_modules"),
      scope = CacheScope.Pipeline
    )
    val step: Step = Step.SaveCache(cache, NonEmptyList.of("/node_modules"))
    val json = step.asJson

    assertEquals(json.hcursor.downField("type").as[String], Right("SaveCache"))

    val decoded = json.as[Step]
    assert(decoded.isRight)
  }

  test("Step.Composite encoding/decoding") {
    given StepMeta = StepMeta(
      id = Some("step-1"),
      timeout = Some(67.minutes) // SIIIIX - SEVEEEEN! ¯\_(ツ)_/¯
    )

    val step1     = Step.Checkout()
    val step2     = Step.Run(Command.Exec("echo", List("hello")))
    val composite = Step.Composite(NonEmptyVector.of(step1, step2))

    val json = composite.asJson
    assertEquals(json.hcursor.downField("type").as[String], Right("Composite"))

    val decoded = json.as[Step]
    decoded match
      case Right(Step.Composite(steps)) =>
        assertEquals(steps.length, 2)
      case _ => fail("Expected Step.Composite")
  }

  // Complex round-trip tests

  test("Complex Command round-trip") {
    val composite = Command.Composite(
      NonEmptyVector.of(
        Command.Exec("npm", List("install"), Map("NODE_ENV" -> "production"), Some("/app"), Some(300)),
        Command.Shell("npm test && npm run build", ShellKind.Bash, Map("CI" -> "true"), None, None),
        Command.Exec("docker", List("build", "-t", "myapp", "."))
      )
    )

    val json    = composite.asJson
    val decoded = json.as[Command]

    decoded match
      case Right(Command.Composite(steps)) =>
        assertEquals(steps.length, 3)

        steps.toVector(0) match
          case Command.Exec(program, args, env, cwd, timeout) =>
            assertEquals(program, "npm")
            assertEquals(args, List("install"))
            assertEquals(env, Map("NODE_ENV" -> "production"))
            assertEquals(cwd, Some("/app"))
            assertEquals(timeout, Some(300))
          case _ => fail("Expected Exec")

        steps.toVector(1) match
          case Command.Shell(script, shell, env, _, _) =>
            assertEquals(script, "npm test && npm run build")
            assertEquals(shell, ShellKind.Bash)
            assertEquals(env, Map("CI" -> "true"))
          case _ => fail("Expected Shell")

        steps.toVector(2) match
          case Command.Exec(program, args, _, _, _) =>
            assertEquals(program, "docker")
            assertEquals(args, List("build", "-t", "myapp", "."))
          case _ => fail("Expected Exec")

      case Left(error) => fail("Decoding failed: " + error.toString)
      case _           => fail("Expected Composite")
  }

  test("Invalid Command type fails decoding") {
    val json = parse("""
    {
      "type": "Invalid",
      "data": "something"
    }
    """).toOption.get

    val decoded = json.as[Command]
    assert(decoded.isLeft)
  }

  test("Invalid Step type fails decoding") {
    val json = parse("""
    {
      "type": "Invalid",
      "data": "something"
    }
    """).toOption.get

    val decoded = json.as[Step]
    assert(decoded.isLeft)
  }

  test("Malformed JSON fails decoding") {
    val json    = Json.fromString("not an object")
    val decoded = json.as[Command]
    assert(decoded.isLeft)
  }
