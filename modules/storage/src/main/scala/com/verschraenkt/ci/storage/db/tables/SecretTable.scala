package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.secretScopeMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.Enums.SecretScope

import java.time.Instant
import java.util.UUID

/** Database row representation of a secret
  *
  * @param secretId
  *   UUID v7 primary key, DB-generated
  * @param name
  *   Human-readable secret name
  * @param scope
  *   Secret scope (global, pipeline, workflow, job)
  * @param scopeEntityId
  *   Entity ID the scope applies to
  * @param vaultPathEncrypted
  *   Encrypted vault path (stored as BYTEA)
  * @param vaultPathKeyId
  *   KMS key ID used for encryption
  * @param version
  *   Secret version number
  * @param createdAt
  *   Creation timestamp
  * @param updatedAt
  *   Last update timestamp
  * @param rotatedAt
  *   Last key rotation timestamp
  * @param createdBy
  *   Who created this secret
  * @param isActive
  *   Whether the secret is currently active
  * @param deletedAt
  *   Soft-delete timestamp
  * @param deletedBy
  *   Who soft-deleted this secret
  */
case class SecretRow(
    secretId: Option[UUID],
    name: String,
    scope: SecretScope,
    scopeEntityId: Option[String],
    vaultPathEncrypted: Array[Byte],
    vaultPathKeyId: String,
    version: Int,
    createdAt: Instant,
    updatedAt: Instant,
    rotatedAt: Option[Instant],
    createdBy: String,
    isActive: Boolean,
    deletedAt: Option[Instant],
    deletedBy: Option[String]
)

class SecretTable(tag: Tag) extends Table[SecretRow](tag, "secrets"):
  def * = (
    secretId.?,
    name,
    scope,
    scopeEntityId,
    vaultPathEncrypted,
    vaultPathKeyId,
    version,
    createdAt,
    updatedAt,
    rotatedAt,
    createdBy,
    isActive,
    deletedAt,
    deletedBy
  ) <> (SecretRow.apply.tupled, SecretRow.unapply)

  def secretId = column[UUID]("secret_id", O.PrimaryKey, O.AutoInc)

  def name = column[String]("name")

  def scope = column[SecretScope]("scope")

  def scopeEntityId = column[Option[String]]("scope_entity_id")

  def vaultPathEncrypted = column[Array[Byte]]("vault_path_encrypted")

  def vaultPathKeyId = column[String]("vault_path_key_id")

  def version = column[Int]("version")

  def createdAt = column[Instant]("created_at")

  def updatedAt = column[Instant]("updated_at")

  def rotatedAt = column[Option[Instant]]("rotated_at")

  def createdBy = column[String]("created_by")

  def isActive = column[Boolean]("is_active")

  def deletedAt = column[Option[Instant]]("deleted_at")

  def deletedBy = column[Option[String]]("deleted_by")
