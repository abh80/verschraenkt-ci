package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{
  cacheScopeTypeMapper,
  cacheStatusMapper,
  storageBackendMapper
}
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.Enums.{ CacheScopeType, CacheStatus, StorageBackend }
import slick.lifted.ProvenShape
import slick.model.ForeignKeyAction.SetNull

import java.time.Instant
import java.util.UUID

case class CacheEntryInsert(
    cacheKey: String,
    cacheVersion: String = "v1",
    scopeType: CacheScopeType,
    scopeValue: Option[String],
    pathsHash: String,
    inputHash: String,
    contentHash: Option[String],
    storageProvider: StorageBackend = StorageBackend.S3,
    storageBucket: String,
    storageKey: String,
    sizeBytes: Long,
    status: CacheStatus = CacheStatus.Creating,
    createdByExecutionId: Option[Long],
    expiresAt: Option[Instant]
)

case class CacheEntryRow(
    cacheId: UUID,
    cacheKey: String,
    cacheVersion: String,
    scopeType: CacheScopeType,
    scopeValue: Option[String],
    pathsHash: String,
    inputHash: String,
    contentHash: Option[String],
    storageProvider: StorageBackend,
    storageBucket: String,
    storageKey: String,
    sizeBytes: Long,
    status: CacheStatus,
    createdByExecutionId: Option[Long],
    createdAt: Instant,
    updatedAt: Instant,
    lastAccessedAt: Instant,
    accessCount: Int,
    expiresAt: Option[Instant]
)

class CacheEntryTable(tag: Tag) extends Table[CacheEntryRow](tag, "cache_entries"):

  // --- Columns ---

  def * : ProvenShape[CacheEntryRow] = (
    cacheId,
    cacheKey,
    cacheVersion,
    scopeType,
    scopeValue,
    pathsHash,
    inputHash,
    contentHash,
    storageProvider,
    storageBucket,
    storageKey,
    sizeBytes,
    status,
    createdByExecutionId,
    createdAt,
    updatedAt,
    lastAccessedAt,
    accessCount,
    expiresAt
  ).mapTo[CacheEntryRow]

  def cacheId = column[UUID]("cache_id", O.PrimaryKey, O.AutoInc)

  def cacheKey = column[String]("cache_key")

  def cacheVersion = column[String]("cache_version")

  def scopeType = column[CacheScopeType]("scope_type")

  def scopeValue = column[Option[String]]("scope_value")

  def pathsHash = column[String]("paths_hash")

  def inputHash = column[String]("input_hash")

  def contentHash = column[Option[String]]("content_hash")

  def storageProvider = column[StorageBackend]("storage_provider")

  def storageBucket = column[String]("storage_bucket")

  def storageKey = column[String]("storage_key")

  def sizeBytes = column[Long]("size_bytes")

  def status = column[CacheStatus]("status")

  def createdByExecutionId = column[Option[Long]]("created_by_execution")

  def createdAt = column[Instant]("created_at")

  def updatedAt = column[Instant]("updated_at")

  def lastAccessedAt = column[Instant]("last_accessed_at")

  def accessCount = column[Int]("access_count")

  // --- Read projection (full row) ---

  def expiresAt = column[Option[Instant]]("expires_at")

  // --- Insert projection (strips DB-generated fields) ---

  def forInsert = (
    cacheKey,
    cacheVersion,
    scopeType,
    scopeValue,
    pathsHash,
    inputHash,
    contentHash,
    storageProvider,
    storageBucket,
    storageKey,
    sizeBytes,
    status,
    createdByExecutionId,
    expiresAt
  ).mapTo[CacheEntryInsert]

  def createdByExecution_fk = foreignKey(
    "created_by_execution_fk",
    createdByExecutionId,
    TableQuery[ExecutionTable]
  )(_.executionId.?, onDelete = SetNull)
