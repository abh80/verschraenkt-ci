package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.StepId
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, StepType }
import com.verschraenkt.ci.storage.fixtures.TestStepExecutions
import munit.FunSuite

class StepExecutionTableSpec extends FunSuite:

  test("StepExecutionRow creates correct checkout step") {
    val row = TestStepExecutions.checkoutStep()

    assertEquals(row.stepKind, StepType.Checkout)
    assertEquals(row.status, ExecutionStatus.Completed)
    assertEquals(row.stepIndex, 0)
    assertEquals(row.commandText, None)
    assertEquals(row.shell, None)
    assertEquals(row.exitCode, Some(0))
    assertEquals(row.continueOnError, false)
  }

  test("StepExecutionRow handles running run step") {
    val row = TestStepExecutions.runStep()

    assertEquals(row.stepKind, StepType.Run)
    assertEquals(row.status, ExecutionStatus.Running)
    assertEquals(row.commandText, Some("sbt test"))
    assertEquals(row.shell, Some("/bin/bash"))
    assert(row.startedAt.isDefined)
    assertEquals(row.completedAt, None)
    assertEquals(row.exitCode, None)
  }

  test("StepExecutionRow handles completed run step") {
    val row = TestStepExecutions.completedRunStep()

    assertEquals(row.stepKind, StepType.Run)
    assertEquals(row.status, ExecutionStatus.Completed)
    assertEquals(row.exitCode, Some(0))
    assert(row.startedAt.isDefined)
    assert(row.completedAt.isDefined)
    assert(row.completedAt.get.isAfter(row.startedAt.get))
  }

  test("StepExecutionRow handles failed run step with error") {
    val row = TestStepExecutions.failedRunStep()

    assertEquals(row.stepKind, StepType.Run)
    assertEquals(row.status, ExecutionStatus.Failed)
    assertEquals(row.exitCode, Some(1))
    assert(row.errorMessage.isDefined)
    assertEquals(row.errorMessage.get, "Process exited with code 1")
  }

  test("StepExecutionRow handles cache restore step") {
    val row = TestStepExecutions.cacheRestoreStep()

    assertEquals(row.stepKind, StepType.CacheRestore)
    assertEquals(row.status, ExecutionStatus.Completed)
    assertEquals(row.continueOnError, true)
    assertEquals(row.commandText, None)
  }

  test("StepExecutionRow handles artifact upload step") {
    val row = TestStepExecutions.artifactUploadStep()

    assertEquals(row.stepKind, StepType.ArtifactUpload)
    assertEquals(row.status, ExecutionStatus.Completed)
    assertEquals(row.exitCode, Some(0))
  }

  test("StepExecutionRow handles pending step") {
    val row = TestStepExecutions.pendingStep()

    assertEquals(row.status, ExecutionStatus.Pending)
    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
    assertEquals(row.exitCode, None)
    assertEquals(row.stdoutLocation, None)
    assertEquals(row.stderrLocation, None)
  }

  test("StepExecutionRow stores StepId correctly") {
    val row = TestStepExecutions.runStep()

    assert(row.stepId.value.startsWith("step-"))
  }

  test("StepExecutionRow handles stdout and stderr locations") {
    val row = TestStepExecutions.checkoutStep()

    assert(row.stdoutLocation.isDefined)
    assert(row.stderrLocation.isDefined)
  }

  test("StepExecutionRow handles different step types") {
    val checkout      = TestStepExecutions.checkoutStep()
    val run           = TestStepExecutions.runStep()
    val cacheRestore  = TestStepExecutions.cacheRestoreStep()
    val artifactUpload = TestStepExecutions.artifactUploadStep()

    assertEquals(checkout.stepKind, StepType.Checkout)
    assertEquals(run.stepKind, StepType.Run)
    assertEquals(cacheRestore.stepKind, StepType.CacheRestore)
    assertEquals(artifactUpload.stepKind, StepType.ArtifactUpload)
  }

  test("StepExecutionRow handles different execution statuses") {
    val pending   = TestStepExecutions.withStatus(ExecutionStatus.Pending)
    val running   = TestStepExecutions.withStatus(ExecutionStatus.Running)
    val completed = TestStepExecutions.withStatus(ExecutionStatus.Completed)
    val failed    = TestStepExecutions.withStatus(ExecutionStatus.Failed)

    assertEquals(pending.status, ExecutionStatus.Pending)
    assertEquals(running.status, ExecutionStatus.Running)
    assertEquals(completed.status, ExecutionStatus.Completed)
    assertEquals(failed.status, ExecutionStatus.Failed)
  }

  test("StepExecutionRow handles continueOnError flag") {
    val continueOnErr = TestStepExecutions.cacheRestoreStep()
    val failOnErr     = TestStepExecutions.runStep()

    assertEquals(continueOnErr.continueOnError, true)
    assertEquals(failOnErr.continueOnError, false)
  }

  test("StepExecutionRow handles step index ordering") {
    val jobExecId = 100L
    val step0     = TestStepExecutions.checkoutStep(jobExecutionId = jobExecId, stepIndex = 0)
    val step1     = TestStepExecutions.runStep(jobExecutionId = jobExecId, stepIndex = 1)
    val step2     = TestStepExecutions.failedRunStep(jobExecutionId = jobExecId, stepIndex = 2)

    assertEquals(step0.stepIndex, 0)
    assertEquals(step1.stepIndex, 1)
    assertEquals(step2.stepIndex, 2)
    assertEquals(step0.jobExecutionId, step1.jobExecutionId)
    assertEquals(step1.jobExecutionId, step2.jobExecutionId)
  }

  test("StepExecutionRow default values for optional fields") {
    val row = StepExecutionRow(
      stepExecutionId = 1L,
      jobExecutionId = 1L,
      stepId = StepId("test"),
      stepKind = StepType.Run,
      stepIndex = 0,
      status = ExecutionStatus.Pending,
      startedAt = None,
      completedAt = None,
      commandText = None,
      shell = None,
      exitCode = None,
      stdoutLocation = None,
      stderrLocation = None,
      errorMessage = None,
      continueOnError = false
    )

    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
    assertEquals(row.commandText, None)
    assertEquals(row.shell, None)
    assertEquals(row.exitCode, None)
    assertEquals(row.stdoutLocation, None)
    assertEquals(row.stderrLocation, None)
    assertEquals(row.errorMessage, None)
  }
