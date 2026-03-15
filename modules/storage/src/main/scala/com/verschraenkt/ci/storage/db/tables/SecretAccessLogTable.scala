package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import slick.model.ForeignKeyAction.Cascade
import com.verschraenkt.ci.storage.db.codecs.ColumnTypes.inetTypeMapper

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

/** Database row representation of a secret access log entry
  *
  * @param logId
  *   BIGSERIAL primary key, DB-generated
  * @param secretId
  *   The secret that was accessed
  * @param jobExecutionId
  *   The job execution that requested access
  * @param accessedAt
  *   When the access occurred
  * @param executorId
  *   Which executor accessed the secret
  * @param granted
  *   Whether access was granted
  * @param denialReason
  *   Reason for denial if not granted
  * @param requestIp
  *   IP address of the request (PostgreSQL INET stored as String)
  */
case class SecretAccessLogRow(
    logId: Option[Long],
    secretId: UUID,
    jobExecutionId: Long,
    accessedAt: Instant,
    executorId: UUID,
    granted: Boolean,
    denialReason: Option[String],
    requestIp: Option[InetAddress]
)

class SecretAccessLogTable(tag: Tag) extends Table[SecretAccessLogRow](tag, "secret_access_log"):
  def * = (
    logId.?,
    secretId,
    jobExecutionId,
    accessedAt,
    executorId,
    granted,
    denialReason,
    requestIp
  ) <> (SecretAccessLogRow.apply.tupled, SecretAccessLogRow.unapply)

  def logId = column[Long]("log_id", O.PrimaryKey, O.AutoInc)

  def accessedAt = column[Instant]("accessed_at")

  def executorId = column[UUID]("executor_id")

  def granted = column[Boolean]("granted")

  def denialReason = column[Option[String]]("denial_reason")

  def requestIp = column[Option[InetAddress]]("request_ip")

  def secretId = column[UUID]("secret_id")

  def jobExecutionId = column[Long]("job_execution_id")

  def secret_fk = foreignKey(
    "secret_access_log_secret_id_fkey",
    secretId,
    TableQuery[SecretTable]
  )(_.secretId, onDelete = Cascade)

  def jobExecution_fk = foreignKey(
    "secret_access_log_job_execution_id_fkey",
    jobExecutionId,
    TableQuery[JobExecutionTable]
  )(_.jobExecutionId, onDelete = Cascade)

  def executor_fk = foreignKey(
    "secret_access_log_executor_id_fkey",
    executorId,
    TableQuery[ExecutorTable]
  )(_.executorId)
