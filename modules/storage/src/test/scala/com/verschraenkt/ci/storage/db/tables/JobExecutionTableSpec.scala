package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.core.model.JobId
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.fixtures.TestJobExecutions
import munit.FunSuite

class JobExecutionTableSpec extends FunSuite:

  test("JobExecutionRow creates correct row with all fields") {
    val row = TestJobExecutions.queuedJobExecution()

    assertEquals(row.status, ExecutionStatus.Pending)
    assertEquals(row.jobName, "build")
    assertEquals(row.dependencies, None)
    assertEquals(row.executorId, None)
    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
    assertEquals(row.exitCode, None)
    assertEquals(row.errorMessage, None)
    assertEquals(row.matrixCoordinates, None)
    assertEquals(row.attemptNumber, 1)
    assertEquals(row.maxAttempts, 3)
  }

  test("JobExecutionRow handles running state with executor assignment") {
    val row = TestJobExecutions.runningJobExecution()

    assertEquals(row.status, ExecutionStatus.Running)
    assert(row.executorId.isDefined)
    assert(row.assignedAt.isDefined)
    assert(row.startedAt.isDefined)
    assertEquals(row.completedAt, None)
  }

  test("JobExecutionRow handles completed state with exit code") {
    val row = TestJobExecutions.completedJobExecution()

    assertEquals(row.status, ExecutionStatus.Completed)
    assertEquals(row.exitCode, Some(0))
    assert(row.startedAt.isDefined)
    assert(row.completedAt.isDefined)
    assert(row.completedAt.get.isAfter(row.startedAt.get))
  }

  test("JobExecutionRow handles failed state with error message") {
    val row = TestJobExecutions.failedJobExecution()

    assertEquals(row.status, ExecutionStatus.Failed)
    assertEquals(row.exitCode, Some(1))
    assert(row.errorMessage.isDefined)
    assertEquals(row.errorMessage.get, "Lint check failed: 5 errors found")
    assertEquals(row.attemptNumber, 3)
    assertEquals(row.maxAttempts, 3)
  }

  test("JobExecutionRow handles matrix coordinates") {
    val row = TestJobExecutions.matrixJobExecution()

    assert(row.matrixCoordinates.isDefined)
    val coords = row.matrixCoordinates.get
    assert(coords.isObject)
    assert(coords.hcursor.get[String]("os").toOption.contains("ubuntu-22.04"))
    assert(coords.hcursor.get[String]("scala").toOption.contains("3.3.1"))
    assert(coords.hcursor.get[Int]("jdk").toOption.contains(17))
  }

  test("JobExecutionRow handles dependencies as JSON array") {
    val row = TestJobExecutions.runningJobExecution()

    assert(row.dependencies.isDefined)
    val deps = row.dependencies.get
    assert(deps.isArray)
  }

  test("JobExecutionRow handles resource requests") {
    val row = TestJobExecutions.queuedJobExecution()

    assertEquals(row.requestedCpuMilli, Some(1000))
    assertEquals(row.requestedMemoryMib, Some(2048))
    assertEquals(row.requestedGpu, None)
    assertEquals(row.requestedDiskMib, Some(10240))
  }

  test("JobExecutionRow handles GPU resource requests") {
    val row = TestJobExecutions.runningJobExecution()

    assertEquals(row.requestedGpu, Some(1))
  }

  test("JobExecutionRow handles actual resource usage") {
    val row = TestJobExecutions.completedJobExecution()

    assert(row.actualCpuMilliSeconds.isDefined)
    assert(row.actualMemoryMibSeconds.isDefined)
    assert(row.actualCpuMilliSeconds.get > 0)
    assert(row.actualMemoryMibSeconds.get > 0)
  }

  test("JobExecutionRow stores JobId correctly") {
    val jobId = JobId("custom-job-id")
    val row   = TestJobExecutions.queuedJobExecution(jobId = jobId)

    assertEquals(row.jobId, jobId)
  }

  test("JobExecutionRow handles different execution statuses") {
    val pending   = TestJobExecutions.withStatus(ExecutionStatus.Pending)
    val running   = TestJobExecutions.withStatus(ExecutionStatus.Running)
    val completed = TestJobExecutions.withStatus(ExecutionStatus.Completed)
    val failed    = TestJobExecutions.withStatus(ExecutionStatus.Failed)

    assertEquals(pending.status, ExecutionStatus.Pending)
    assertEquals(running.status, ExecutionStatus.Running)
    assertEquals(completed.status, ExecutionStatus.Completed)
    assertEquals(failed.status, ExecutionStatus.Failed)
  }

  test("JobExecutionRow handles scheduling timestamps") {
    val row = TestJobExecutions.runningJobExecution()

    assert(row.readyAt.isDefined)
    assert(row.scheduledAt.isDefined)
    assert(row.assignedAt.isDefined)
    assert(row.scheduledAt.get.isAfter(row.readyAt.get))
    assert(row.assignedAt.get.isAfter(row.scheduledAt.get))
  }

  test("JobExecutionRow queued has no scheduling timestamps") {
    val row = TestJobExecutions.queuedJobExecution()

    assertEquals(row.readyAt, None)
    assertEquals(row.scheduledAt, None)
    assertEquals(row.assignedAt, None)
  }

  test("JobExecutionRow default values for optional fields") {
    val row = JobExecutionRow(
      jobExecutionId = 1L,
      workflowExecutionId = 1L,
      executionId = 1L,
      jobId = JobId("test"),
      jobName = "test",
      status = ExecutionStatus.Pending,
      dependencies = None,
      readyAt = None,
      scheduledAt = None,
      executorId = None,
      assignedAt = None,
      startedAt = None,
      completedAt = None,
      timeoutAt = None,
      requestedCpuMilli = None,
      requestedMemoryMib = None,
      requestedGpu = None,
      requestedDiskMib = None,
      actualCpuMilliSeconds = None,
      actualMemoryMibSeconds = None,
      attemptNumber = 1,
      maxAttempts = 1,
      exitCode = None,
      errorMessage = None,
      matrixCoordinates = None
    )

    assertEquals(row.dependencies, None)
    assertEquals(row.executorId, None)
    assertEquals(row.exitCode, None)
    assertEquals(row.errorMessage, None)
    assertEquals(row.matrixCoordinates, None)
  }

  test("JobExecutionRow handles retry attempts") {
    val row = TestJobExecutions.failedJobExecution()

    assertEquals(row.attemptNumber, 3)
    assertEquals(row.maxAttempts, 3)
    assert(row.attemptNumber <= row.maxAttempts)
  }
