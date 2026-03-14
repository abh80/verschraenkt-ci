package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.storage.db.codecs.Enums.{ CacheScopeType, CacheStatus, StorageBackend }
import com.verschraenkt.ci.storage.db.tables.{ CacheEntryInsert, CacheEntryRow }

import java.time.Instant
import java.util.UUID

/** Reusable test data for cache entry tests */
object TestCacheEntries:

  private var counter = 0

  private def getCounter: String =
    counter += 1
    counter.toString

  def creatingEntry(
      cacheKey: String = s"cache-key-${getCounter}",
      scopeType: CacheScopeType = CacheScopeType.Global
  ): CacheEntryInsert =
    CacheEntryInsert(
      cacheKey = cacheKey,
      cacheVersion = "v1",
      scopeType = scopeType,
      scopeValue = None,
      pathsHash = "a" * 64,
      inputHash = "b" * 64,
      contentHash = None,
      storageProvider = StorageBackend.S3,
      storageBucket = "ci-cache",
      storageKey = s"cache/$cacheKey/data.tar.gz",
      sizeBytes = 1024000L,
      status = CacheStatus.Creating,
      createdByExecutionId = None,
      expiresAt = Some(Instant.now().plusSeconds(86400 * 30))
    )

  def readyEntry(
      cacheKey: String = s"cache-key-${getCounter}",
      scopeType: CacheScopeType = CacheScopeType.Global
  ): CacheEntryInsert =
    CacheEntryInsert(
      cacheKey = cacheKey,
      cacheVersion = "v1",
      scopeType = scopeType,
      scopeValue = None,
      pathsHash = "c" * 64,
      inputHash = "d" * 64,
      contentHash = Some("e" * 64),
      storageProvider = StorageBackend.S3,
      storageBucket = "ci-cache",
      storageKey = s"cache/$cacheKey/data.tar.gz",
      sizeBytes = 2048000L,
      status = CacheStatus.Ready,
      createdByExecutionId = None,
      expiresAt = Some(Instant.now().plusSeconds(86400 * 30))
    )

  def branchScopedEntry(
      cacheKey: String = s"cache-key-${getCounter}",
      branch: String = "main"
  ): CacheEntryInsert =
    CacheEntryInsert(
      cacheKey = cacheKey,
      cacheVersion = "v1",
      scopeType = CacheScopeType.Branch,
      scopeValue = Some(branch),
      pathsHash = "f" * 64,
      inputHash = "0" * 64,
      contentHash = Some("1" * 64),
      storageProvider = StorageBackend.S3,
      storageBucket = "ci-cache",
      storageKey = s"cache/$branch/$cacheKey/data.tar.gz",
      sizeBytes = 512000L,
      status = CacheStatus.Ready,
      createdByExecutionId = None,
      expiresAt = None
    )

  def minioEntry(
      cacheKey: String = s"cache-key-${getCounter}"
  ): CacheEntryInsert =
    creatingEntry(cacheKey).copy(
      storageProvider = StorageBackend.Minio,
      storageBucket = "local-cache"
    )

  def expiredEntry(
      cacheKey: String = s"cache-key-${getCounter}"
  ): CacheEntryInsert =
    CacheEntryInsert(
      cacheKey = cacheKey,
      cacheVersion = "v1",
      scopeType = CacheScopeType.Global,
      scopeValue = None,
      pathsHash = "2" * 64,
      inputHash = "3" * 64,
      contentHash = Some("4" * 64),
      storageProvider = StorageBackend.S3,
      storageBucket = "ci-cache",
      storageKey = s"cache/$cacheKey/data.tar.gz",
      sizeBytes = 4096000L,
      status = CacheStatus.Ready,
      createdByExecutionId = None,
      expiresAt = Some(Instant.now().minusSeconds(86400))
    )

  def rowFromInsert(insert: CacheEntryInsert): CacheEntryRow =
    CacheEntryRow(
      cacheId = UUID.randomUUID(),
      cacheKey = insert.cacheKey,
      cacheVersion = insert.cacheVersion,
      scopeType = insert.scopeType,
      scopeValue = insert.scopeValue,
      pathsHash = insert.pathsHash,
      inputHash = insert.inputHash,
      contentHash = insert.contentHash,
      storageProvider = insert.storageProvider,
      storageBucket = insert.storageBucket,
      storageKey = insert.storageKey,
      sizeBytes = insert.sizeBytes,
      status = insert.status,
      createdByExecutionId = insert.createdByExecutionId,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      lastAccessedAt = Instant.now(),
      accessCount = 0,
      expiresAt = insert.expiresAt
    )
