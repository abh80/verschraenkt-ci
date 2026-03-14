package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.Enums.{ CacheScopeType, CacheStatus, StorageBackend }
import com.verschraenkt.ci.storage.fixtures.TestCacheEntries
import munit.FunSuite

import java.time.Instant
import java.util.UUID

class CacheEntryTableSpec extends FunSuite:

  // --- CacheEntryInsert tests ---

  test("CacheEntryInsert creates entry in Creating status") {
    val insert = TestCacheEntries.creatingEntry()

    assert(insert.cacheKey.startsWith("cache-key-"))
    assertEquals(insert.cacheVersion, "v1")
    assertEquals(insert.scopeType, CacheScopeType.Global)
    assertEquals(insert.scopeValue, None)
    assertEquals(insert.storageProvider, StorageBackend.S3)
    assertEquals(insert.status, CacheStatus.Creating)
    assertEquals(insert.contentHash, None)
    assertEquals(insert.createdByExecutionId, None)
    assert(insert.expiresAt.isDefined)
    assert(insert.sizeBytes > 0)
  }

  test("CacheEntryInsert creates entry in Ready status") {
    val insert = TestCacheEntries.readyEntry()

    assertEquals(insert.status, CacheStatus.Ready)
    assert(insert.contentHash.isDefined)
  }

  test("CacheEntryInsert creates branch-scoped entry") {
    val insert = TestCacheEntries.branchScopedEntry(branch = "feature/test")

    assertEquals(insert.scopeType, CacheScopeType.Branch)
    assertEquals(insert.scopeValue, Some("feature/test"))
    assertEquals(insert.status, CacheStatus.Ready)
    assert(insert.storageKey.contains("feature/test"))
  }

  test("CacheEntryInsert creates minio-backed entry") {
    val insert = TestCacheEntries.minioEntry()

    assertEquals(insert.storageProvider, StorageBackend.Minio)
    assertEquals(insert.storageBucket, "local-cache")
  }

  test("CacheEntryInsert creates expired entry") {
    val insert = TestCacheEntries.expiredEntry()

    assert(insert.expiresAt.isDefined)
    assert(insert.expiresAt.get.isBefore(Instant.now()))
  }

  test("CacheEntryInsert preserves custom cache key") {
    val insert = TestCacheEntries.creatingEntry(cacheKey = "my-custom-key")

    assertEquals(insert.cacheKey, "my-custom-key")
    assert(insert.storageKey.contains("my-custom-key"))
  }

  test("CacheEntryInsert preserves custom scope type") {
    val insert = TestCacheEntries.creatingEntry(scopeType = CacheScopeType.Pr)

    assertEquals(insert.scopeType, CacheScopeType.Pr)
  }

  test("CacheEntryInsert default values") {
    val insert = CacheEntryInsert(
      cacheKey = "test",
      scopeType = CacheScopeType.Global,
      scopeValue = None,
      pathsHash = "hash1",
      inputHash = "hash2",
      contentHash = None,
      storageBucket = "bucket",
      storageKey = "key",
      sizeBytes = 0L,
      createdByExecutionId = None,
      expiresAt = None
    )

    assertEquals(insert.cacheVersion, "v1")
    assertEquals(insert.storageProvider, StorageBackend.S3)
    assertEquals(insert.status, CacheStatus.Creating)
  }

  // --- CacheEntryRow tests ---

  test("CacheEntryRow has all DB-generated fields") {
    val row = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry())

    assert(row.cacheId != null)
    assert(row.createdAt != null)
    assert(row.updatedAt != null)
    assert(row.lastAccessedAt != null)
    assertEquals(row.accessCount, 0)
  }

  test("CacheEntryRow preserves insert fields") {
    val insert = TestCacheEntries.readyEntry(cacheKey = "preserved-key")
    val row    = TestCacheEntries.rowFromInsert(insert)

    assertEquals(row.cacheKey, "preserved-key")
    assertEquals(row.cacheVersion, insert.cacheVersion)
    assertEquals(row.scopeType, insert.scopeType)
    assertEquals(row.scopeValue, insert.scopeValue)
    assertEquals(row.pathsHash, insert.pathsHash)
    assertEquals(row.inputHash, insert.inputHash)
    assertEquals(row.contentHash, insert.contentHash)
    assertEquals(row.storageProvider, insert.storageProvider)
    assertEquals(row.storageBucket, insert.storageBucket)
    assertEquals(row.storageKey, insert.storageKey)
    assertEquals(row.sizeBytes, insert.sizeBytes)
    assertEquals(row.status, insert.status)
    assertEquals(row.createdByExecutionId, insert.createdByExecutionId)
    assertEquals(row.expiresAt, insert.expiresAt)
  }

  test("CacheEntryRow handles all scope types") {
    val global = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry(scopeType = CacheScopeType.Global))
    val branch = TestCacheEntries.rowFromInsert(TestCacheEntries.branchScopedEntry())
    val pr     = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry(scopeType = CacheScopeType.Pr))
    val repo   = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry(scopeType = CacheScopeType.Repo))
    val commit = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry(scopeType = CacheScopeType.Commit))

    assertEquals(global.scopeType, CacheScopeType.Global)
    assertEquals(branch.scopeType, CacheScopeType.Branch)
    assertEquals(pr.scopeType, CacheScopeType.Pr)
    assertEquals(repo.scopeType, CacheScopeType.Repo)
    assertEquals(commit.scopeType, CacheScopeType.Commit)
  }

  test("CacheEntryRow handles all cache statuses") {
    val creating = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry())
    val ready    = TestCacheEntries.rowFromInsert(TestCacheEntries.readyEntry())

    assertEquals(creating.status, CacheStatus.Creating)
    assertEquals(ready.status, CacheStatus.Ready)
  }

  test("CacheEntryRow handles all storage backends") {
    val s3    = TestCacheEntries.rowFromInsert(TestCacheEntries.creatingEntry())
    val minio = TestCacheEntries.rowFromInsert(TestCacheEntries.minioEntry())

    assertEquals(s3.storageProvider, StorageBackend.S3)
    assertEquals(minio.storageProvider, StorageBackend.Minio)
  }

  test("CacheEntryRow handles optional fields") {
    val withOptionals = TestCacheEntries.rowFromInsert(TestCacheEntries.readyEntry())
    val withoutOptionals = TestCacheEntries.rowFromInsert(
      TestCacheEntries.creatingEntry().copy(
        scopeValue = None,
        contentHash = None,
        createdByExecutionId = None,
        expiresAt = None
      )
    )

    assert(withOptionals.contentHash.isDefined)
    assertEquals(withoutOptionals.scopeValue, None)
    assertEquals(withoutOptionals.contentHash, None)
    assertEquals(withoutOptionals.createdByExecutionId, None)
    assertEquals(withoutOptionals.expiresAt, None)
  }

  test("CacheEntryRow default values for all fields") {
    val row = CacheEntryRow(
      cacheId = UUID.randomUUID(),
      cacheKey = "test-key",
      cacheVersion = "v1",
      scopeType = CacheScopeType.Global,
      scopeValue = None,
      pathsHash = "hash1",
      inputHash = "hash2",
      contentHash = None,
      storageProvider = StorageBackend.S3,
      storageBucket = "bucket",
      storageKey = "key",
      sizeBytes = 0L,
      status = CacheStatus.Creating,
      createdByExecutionId = None,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
      lastAccessedAt = Instant.now(),
      accessCount = 0,
      expiresAt = None
    )

    assertEquals(row.cacheKey, "test-key")
    assertEquals(row.cacheVersion, "v1")
    assertEquals(row.scopeValue, None)
    assertEquals(row.contentHash, None)
    assertEquals(row.createdByExecutionId, None)
    assertEquals(row.accessCount, 0)
    assertEquals(row.expiresAt, None)
  }

  test("CacheEntryRow handles hash format for pathsHash and inputHash") {
    val insert = TestCacheEntries.creatingEntry()

    assertEquals(insert.pathsHash.length, 64)
    assertEquals(insert.inputHash.length, 64)
    assert(insert.pathsHash.matches("^[a-fA-F0-9]{64}$"))
    assert(insert.inputHash.matches("^[a-fA-F0-9]{64}$"))
  }

  test("CacheEntryRow handles createdByExecutionId") {
    val insert = TestCacheEntries.creatingEntry().copy(createdByExecutionId = Some(42L))
    val row    = TestCacheEntries.rowFromInsert(insert)

    assertEquals(row.createdByExecutionId, Some(42L))
  }

  test("CacheEntryRow branch-scoped entry has scopeValue set") {
    val insert = TestCacheEntries.branchScopedEntry(branch = "develop")
    val row    = TestCacheEntries.rowFromInsert(insert)

    assertEquals(row.scopeType, CacheScopeType.Branch)
    assertEquals(row.scopeValue, Some("develop"))
  }

  test("CacheEntryRow global-scoped entry has no scopeValue") {
    val insert = TestCacheEntries.creatingEntry()
    val row    = TestCacheEntries.rowFromInsert(insert)

    assertEquals(row.scopeType, CacheScopeType.Global)
    assertEquals(row.scopeValue, None)
  }
