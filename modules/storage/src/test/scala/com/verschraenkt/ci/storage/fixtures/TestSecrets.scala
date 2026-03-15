package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.storage.db.codecs.Enums.SecretScope
import com.verschraenkt.ci.storage.db.tables.{ SecretAccessLogRow, SecretRow }

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

/** Reusable test data for secret and secret access log tests */
object TestSecrets:

  private var counter = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  def secret(
      name: String = s"secret-${getCounter}",
      scope: SecretScope = SecretScope.Global,
      scopeEntityId: Option[String] = None,
      createdBy: String = "test-user"
  ): SecretRow =
    SecretRow(
      secretId = None,
      name = name,
      scope = scope,
      scopeEntityId = scopeEntityId,
      vaultPathEncrypted = s"vault/secrets/$name".getBytes("UTF-8"),
      vaultPathKeyId = "key-001",
      version = 1,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      rotatedAt = None,
      createdBy = createdBy,
      isActive = true,
      deletedAt = None,
      deletedBy = None
    )

  def pipelineScopedSecret(
      name: String = s"secret-pipeline-${getCounter}",
      pipelineId: String = "test-pipeline"
  ): SecretRow =
    secret(name = name, scope = SecretScope.Pipeline, scopeEntityId = Some(pipelineId))

  def inactiveSecret(
      name: String = s"secret-inactive-${getCounter}"
  ): SecretRow =
    secret(name = name).copy(isActive = false)

  def rotatedSecret(
      name: String = s"secret-rotated-${getCounter}"
  ): SecretRow =
    secret(name = name).copy(
      version = 2,
      rotatedAt = Some(Instant.now().minusSeconds(3600))
    )

  def deletedSecret(
      name: String = s"secret-deleted-${getCounter}"
  ): SecretRow =
    secret(name = name).copy(
      isActive = false,
      deletedAt = Some(Instant.now().minusSeconds(86400)),
      deletedBy = Some("admin-user")
    )

  def accessLog(
      secretId: UUID = UUID.randomUUID(),
      jobExecutionId: Long = 1L,
      executorId: UUID = UUID.randomUUID(),
      granted: Boolean = true
  ): SecretAccessLogRow =
    SecretAccessLogRow(
      logId = None,
      secretId = secretId,
      jobExecutionId = jobExecutionId,
      accessedAt = Instant.now(),
      executorId = executorId,
      granted = granted,
      denialReason = if granted then None else Some("Scope mismatch"),
      requestIp = Some(InetAddress.getByName("10.0.0.1"))
    )

  def deniedAccessLog(
      secretId: UUID = UUID.randomUUID(),
      jobExecutionId: Long = 1L,
      executorId: UUID = UUID.randomUUID(),
      reason: String = "Scope mismatch"
  ): SecretAccessLogRow =
    SecretAccessLogRow(
      logId = None,
      secretId = secretId,
      jobExecutionId = jobExecutionId,
      accessedAt = Instant.now(),
      executorId = executorId,
      granted = false,
      denialReason = Some(reason),
      requestIp = Some(InetAddress.getByName("10.0.0.2"))
    )
