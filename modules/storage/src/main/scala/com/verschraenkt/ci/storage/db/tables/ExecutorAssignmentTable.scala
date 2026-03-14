package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import slick.model.ForeignKeyAction.Cascade

import java.time.Instant
import java.util.UUID

/** Database row representation of an executor assignment
  *
  * @param assignmentId
  *   UUID v7 primary key, DB-generated
  * @param jobExecutionId
  *   The job execution this assignment is for (unique constraint)
  * @param executorId
  *   The executor assigned to the job
  * @param assignedAt
  *   When the assignment was created
  * @param startedAt
  *   When the executor started working on the job
  * @param completedAt
  *   When the executor finished the job
  */
case class ExecutorAssignmentRow(
    assignmentId: Option[UUID],
    jobExecutionId: Long,
    executorId: UUID,
    assignedAt: Instant,
    startedAt: Option[Instant],
    completedAt: Option[Instant]
)

class ExecutorAssignmentTable(tag: Tag) extends Table[ExecutorAssignmentRow](tag, "executor_assignments"):
  def assignmentId   = column[UUID]("assignment_id", O.PrimaryKey, O.AutoInc)
  def jobExecutionId = column[Long]("job_execution_id", O.Unique)
  def executorId     = column[UUID]("executor_id")
  def assignedAt     = column[Instant]("assigned_at")
  def startedAt      = column[Option[Instant]]("started_at")
  def completedAt    = column[Option[Instant]]("completed_at")

  def * = (
    assignmentId.?,
    jobExecutionId,
    executorId,
    assignedAt,
    startedAt,
    completedAt
  ) <> (ExecutorAssignmentRow.apply.tupled, ExecutorAssignmentRow.unapply)

  def fk_jobExecutionId =
    foreignKey("executor_assignments_job_execution_id_fkey", jobExecutionId, TableQuery[JobExecutionTable])(
      _.jobExecutionId,
      onDelete = Cascade
    )

  def fk_executorId =
    foreignKey("executor_assignments_executor_id_fkey", executorId, TableQuery[ExecutorTable])(
      _.executorId,
      onDelete = Cascade
    )
