package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.fixtures.TestExecutorAssignments
import munit.FunSuite

import java.time.Instant
import java.util.UUID

class ExecutorAssignmentTableSpec extends FunSuite:

  test("ExecutorAssignmentRow creates pending assignment with no start/completion") {
    val row = TestExecutorAssignments.pendingAssignment()

    assertEquals(row.assignmentId, None)
    assert(row.jobExecutionId > 0)
    assert(row.executorId != null)
    assert(row.assignedAt != null)
    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
  }

  test("ExecutorAssignmentRow creates active assignment with start time") {
    val row = TestExecutorAssignments.activeAssignment()

    assertEquals(row.assignmentId, None)
    assert(row.startedAt.isDefined)
    assertEquals(row.completedAt, None)
    assert(row.startedAt.get.isAfter(row.assignedAt))
  }

  test("ExecutorAssignmentRow creates completed assignment with all timestamps") {
    val row = TestExecutorAssignments.completedAssignment()

    assertEquals(row.assignmentId, None)
    assert(row.startedAt.isDefined)
    assert(row.completedAt.isDefined)
    assert(row.startedAt.get.isAfter(row.assignedAt))
    assert(row.completedAt.get.isAfter(row.startedAt.get))
  }

  test("ExecutorAssignmentRow preserves specific jobExecutionId") {
    val jobExecId = 42L
    val row       = TestExecutorAssignments.pendingAssignment(jobExecutionId = jobExecId)

    assertEquals(row.jobExecutionId, jobExecId)
  }

  test("ExecutorAssignmentRow preserves specific executorId") {
    val execId = UUID.randomUUID()
    val row    = TestExecutorAssignments.pendingAssignment(executorId = execId)

    assertEquals(row.executorId, execId)
  }

  test("ExecutorAssignmentRow default values for optional fields") {
    val row = ExecutorAssignmentRow(
      assignmentId = None,
      jobExecutionId = 1L,
      executorId = UUID.randomUUID(),
      assignedAt = Instant.now(),
      startedAt = None,
      completedAt = None
    )

    assertEquals(row.assignmentId, None)
    assertEquals(row.startedAt, None)
    assertEquals(row.completedAt, None)
  }
