package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.tables.ExecutorAssignmentRow

import java.time.Instant
import java.util.UUID

/** Reusable test data for executor assignment tests */
object TestExecutorAssignments:

  private val snowflakeProvider = SnowflakeProvider.make(76)

  private def nextId = snowflakeProvider.nextId().value

  def pendingAssignment(
      jobExecutionId: Long = nextId,
      executorId: UUID = UUID.randomUUID()
  ): ExecutorAssignmentRow =
    ExecutorAssignmentRow(
      assignmentId = None,
      jobExecutionId = jobExecutionId,
      executorId = executorId,
      assignedAt = Instant.now(),
      startedAt = None,
      completedAt = None
    )

  def activeAssignment(
      jobExecutionId: Long = nextId,
      executorId: UUID = UUID.randomUUID()
  ): ExecutorAssignmentRow =
    ExecutorAssignmentRow(
      assignmentId = None,
      jobExecutionId = jobExecutionId,
      executorId = executorId,
      assignedAt = Instant.now().minusSeconds(120),
      startedAt = Some(Instant.now().minusSeconds(60)),
      completedAt = None
    )

  def completedAssignment(
      jobExecutionId: Long = nextId,
      executorId: UUID = UUID.randomUUID()
  ): ExecutorAssignmentRow =
    ExecutorAssignmentRow(
      assignmentId = None,
      jobExecutionId = jobExecutionId,
      executorId = executorId,
      assignedAt = Instant.now().minusSeconds(300),
      startedAt = Some(Instant.now().minusSeconds(240)),
      completedAt = Some(Instant.now().minusSeconds(60))
    )
