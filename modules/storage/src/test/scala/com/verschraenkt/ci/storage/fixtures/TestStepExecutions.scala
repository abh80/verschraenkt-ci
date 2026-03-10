package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.core.model.StepId
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.codecs.Enums.{ ExecutionStatus, StepType }
import com.verschraenkt.ci.storage.db.tables.StepExecutionRow

import java.time.Instant

/** Reusable test data for step execution tests */
object TestStepExecutions:

  private val snowflakeProvider = SnowflakeProvider.make(71)
  private var counter           = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  private def nextId = snowflakeProvider.nextId().value

  def checkoutStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 0
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.Checkout,
      stepIndex = stepIndex,
      status = ExecutionStatus.Completed,
      startedAt = Some(Instant.now().minusSeconds(120)),
      completedAt = Some(Instant.now().minusSeconds(110)),
      commandText = None,
      shell = None,
      exitCode = Some(0),
      stdoutLocation = Some("s3://logs/step-checkout/stdout.log"),
      stderrLocation = Some("s3://logs/step-checkout/stderr.log"),
      errorMessage = None,
      continueOnError = false
    )

  def runStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 1
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.Run,
      stepIndex = stepIndex,
      status = ExecutionStatus.Running,
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = None,
      commandText = Some("sbt test"),
      shell = Some("/bin/bash"),
      exitCode = None,
      stdoutLocation = Some("s3://logs/step-run/stdout.log"),
      stderrLocation = Some("s3://logs/step-run/stderr.log"),
      errorMessage = None,
      continueOnError = false
    )

  def completedRunStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 1
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.Run,
      stepIndex = stepIndex,
      status = ExecutionStatus.Completed,
      startedAt = Some(Instant.now().minusSeconds(120)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      commandText = Some("echo 'Hello World'"),
      shell = Some("/bin/bash"),
      exitCode = Some(0),
      stdoutLocation = Some("s3://logs/step-run-done/stdout.log"),
      stderrLocation = Some("s3://logs/step-run-done/stderr.log"),
      errorMessage = None,
      continueOnError = false
    )

  def failedRunStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 2
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.Run,
      stepIndex = stepIndex,
      status = ExecutionStatus.Failed,
      startedAt = Some(Instant.now().minusSeconds(120)),
      completedAt = Some(Instant.now().minusSeconds(60)),
      commandText = Some("npm test"),
      shell = Some("/bin/sh"),
      exitCode = Some(1),
      stdoutLocation = Some("s3://logs/step-failed/stdout.log"),
      stderrLocation = Some("s3://logs/step-failed/stderr.log"),
      errorMessage = Some("Process exited with code 1"),
      continueOnError = false
    )

  def cacheRestoreStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 1
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.CacheRestore,
      stepIndex = stepIndex,
      status = ExecutionStatus.Completed,
      startedAt = Some(Instant.now().minusSeconds(60)),
      completedAt = Some(Instant.now().minusSeconds(50)),
      commandText = None,
      shell = None,
      exitCode = Some(0),
      stdoutLocation = None,
      stderrLocation = None,
      errorMessage = None,
      continueOnError = true
    )

  def artifactUploadStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 3
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.ArtifactUpload,
      stepIndex = stepIndex,
      status = ExecutionStatus.Completed,
      startedAt = Some(Instant.now().minusSeconds(30)),
      completedAt = Some(Instant.now().minusSeconds(10)),
      commandText = None,
      shell = None,
      exitCode = Some(0),
      stdoutLocation = None,
      stderrLocation = None,
      errorMessage = None,
      continueOnError = false
    )

  def pendingStep(
      jobExecutionId: Long = nextId,
      stepIndex: Int = 4
  ): StepExecutionRow =
    StepExecutionRow(
      stepExecutionId = nextId,
      jobExecutionId = jobExecutionId,
      stepId = StepId(s"step-$getCounter"),
      stepKind = StepType.Run,
      stepIndex = stepIndex,
      status = ExecutionStatus.Pending,
      startedAt = None,
      completedAt = None,
      commandText = Some("make build"),
      shell = Some("/bin/bash"),
      exitCode = None,
      stdoutLocation = None,
      stderrLocation = None,
      errorMessage = None,
      continueOnError = false
    )

  def withStatus(status: ExecutionStatus): StepExecutionRow =
    runStep().copy(status = status)
