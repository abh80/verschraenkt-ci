package com.verschraenkt.ci.engine.converter

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.*
import com.verschraenkt.ci.engine.api.{ JobDefinition, StepDefinition }
import munit.FunSuite

import scala.concurrent.duration.*

class ModelConverterSpec extends FunSuite:

  test("convert simple job with one run step") {
    val cmd  = Command.Exec("echo", List("hello"))
    val step = Step.Run(cmd)(using StepMeta(id = Some("test-step")))
    val job  = Job.one(JobId("job-1"), step)

    val jobDef = ModelConverter.toJobDefinition(job)

    assertEquals(jobDef.jobId.value, "job-1")
    assertEquals(jobDef.steps.size, 1)
    val stepDef = jobDef.steps.head
    assertEquals(stepDef.name, "test-step")
    assertEquals(stepDef.command, "echo")
    assertEquals(stepDef.args, List("hello"))
  }

  test("convert job with composite steps (flattening)") {
    val cmd1  = Command.Exec("echo", List("1"))
    val step1 = Step.Run(cmd1)(using StepMeta(id = Some("s1")))

    val cmd2  = Command.Exec("echo", List("2"))
    val step2 = Step.Run(cmd2)(using StepMeta(id = Some("s2")))

    val composite = Step.Composite(NonEmptyVector.of(step1, step2))
    val job       = Job.one(JobId("job-comp"), composite)

    val jobDef = ModelConverter.toJobDefinition(job)

    assertEquals(jobDef.steps.size, 2)
    assertEquals(jobDef.steps(0).name, "s1")
    assertEquals(jobDef.steps(1).name, "s2")
  }

  test("convert shell command") {
    val cmd  = Command.Shell("echo hello", ShellKind.Bash)
    val step = Step.Run(cmd)(using StepMeta())
    val job  = Job.one(JobId("job-shell"), step)

    val jobDef = ModelConverter.toJobDefinition(job)

    assertEquals(jobDef.steps.size, 1)
    assertEquals(jobDef.steps.head.command, "bash")
    assertEquals(jobDef.steps.head.args, List("-c", "echo hello"))
  }

  test("convert composite command (flattening)") {
    val c1      = Command.Exec("A")
    val c2      = Command.Exec("B")
    val compCmd = Command.Composite(NonEmptyVector.of(c1, c2))
    val step    = Step.Run(compCmd)(using StepMeta(id = Some("run-comp")))
    val job     = Job.one(JobId("job-cmd-comp"), step)

    val jobDef = ModelConverter.toJobDefinition(job)

    assertEquals(jobDef.steps.size, 2)
    assertEquals(jobDef.steps(0).command, "A")
    assertEquals(jobDef.steps(1).command, "B")
    // Meta reuse check (both get same meta currently)
    assertEquals(jobDef.steps(0).name, "run-comp")
    assertEquals(jobDef.steps(1).name, "run-comp")
  }
