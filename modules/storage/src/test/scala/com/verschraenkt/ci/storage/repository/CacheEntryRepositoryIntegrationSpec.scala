package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.storage.db.codecs.Enums.{ CacheScopeType, CacheStatus }
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestCacheEntries,
  TestDatabaseFixture
}
import munit.CatsEffectSuite

import java.util.UUID

class CacheEntryRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private def withRepo[A](f: CacheEntryRepository => IO[A]): IO[A] =
    withDatabase { db =>
      f(new CacheEntryRepository(db))
    }

  // --- create ---

  test("create inserts cache entry and returns generated ID") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry()

      for
        id     <- repo.create(entry)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.cacheKey, entry.cacheKey)
          assertEquals(result.get.cacheVersion, entry.cacheVersion)
          assertEquals(result.get.scopeType, entry.scopeType)
          assertEquals(result.get.status, CacheStatus.Creating)
          assertEquals(result.get.sizeBytes, entry.sizeBytes)
          assertEquals(result.get.accessCount, 0)
        }
      yield ()
    }
  }

  test("create persists all fields correctly") {
    withRepo { repo =>
      val entry = TestCacheEntries.branchScopedEntry(cacheKey = "full-fields", branch = "develop")

      for
        id     <- repo.create(entry)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          val r = result.get
          assertEquals(r.cacheKey, "full-fields")
          assertEquals(r.scopeType, CacheScopeType.Branch)
          assertEquals(r.scopeValue, Some("develop"))
          assertEquals(r.pathsHash, entry.pathsHash)
          assertEquals(r.inputHash, entry.inputHash)
          assertEquals(r.contentHash, entry.contentHash)
          assertEquals(r.storageProvider, entry.storageProvider)
          assertEquals(r.storageBucket, entry.storageBucket)
          assertEquals(r.storageKey, entry.storageKey)
        }
      yield ()
    }
  }

  // --- findById ---

  test("findById returns None when cache entry does not exist") {
    withRepo { repo =>
      repo.findById(UUID.randomUUID()).map(r => assert(r.isEmpty))
    }
  }

  // --- findByKeyAndScope ---

  test("findByKeyAndScope returns matching ready entry") {
    withRepo { repo =>
      val entry = TestCacheEntries.readyEntry(cacheKey = "lookup-key")

      for
        id <- repo.create(entry)
        result <- repo.findByKeyAndScope(
          cacheKey = "lookup-key",
          scopeType = entry.scopeType,
          scopeValue = entry.scopeValue,
          pathsHash = entry.pathsHash,
          inputHash = entry.inputHash
        )
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.cacheId, id)
        }
      yield ()
    }
  }

  test("findByKeyAndScope does not return Creating entries") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry(cacheKey = "not-ready-key")

      for
        _ <- repo.create(entry)
        result <- repo.findByKeyAndScope(
          cacheKey = "not-ready-key",
          scopeType = entry.scopeType,
          scopeValue = entry.scopeValue,
          pathsHash = entry.pathsHash,
          inputHash = entry.inputHash
        )
        _ <- IO(assert(result.isEmpty, "Should not find entries in Creating status"))
      yield ()
    }
  }

  test("findByKeyAndScope returns None for non-matching hash") {
    withRepo { repo =>
      val entry = TestCacheEntries.readyEntry(cacheKey = "hash-mismatch")

      for
        _ <- repo.create(entry)
        result <- repo.findByKeyAndScope(
          cacheKey = "hash-mismatch",
          scopeType = entry.scopeType,
          scopeValue = entry.scopeValue,
          pathsHash = "x" * 64,
          inputHash = entry.inputHash
        )
        _ <- IO(assert(result.isEmpty))
      yield ()
    }
  }

  test("findByKeyAndScope with branch scope") {
    withRepo { repo =>
      val entry = TestCacheEntries.branchScopedEntry(cacheKey = "branch-lookup", branch = "feature/x")

      for
        id <- repo.create(entry)
        result <- repo.findByKeyAndScope(
          cacheKey = "branch-lookup",
          scopeType = CacheScopeType.Branch,
          scopeValue = Some("feature/x"),
          pathsHash = entry.pathsHash,
          inputHash = entry.inputHash
        )
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.cacheId, id)
        }
      yield ()
    }
  }

  test("findByKeyAndScope does not cross scope values") {
    withRepo { repo =>
      val entry = TestCacheEntries.branchScopedEntry(cacheKey = "scope-cross", branch = "main")

      for
        _ <- repo.create(entry)
        result <- repo.findByKeyAndScope(
          cacheKey = "scope-cross",
          scopeType = CacheScopeType.Branch,
          scopeValue = Some("develop"),
          pathsHash = entry.pathsHash,
          inputHash = entry.inputHash
        )
        _ <- IO(assert(result.isEmpty, "Should not find entry from a different branch"))
      yield ()
    }
  }

  // --- findByKey ---

  test("findByKey returns all entries for a key and scope") {
    withRepo { repo =>
      val entry1 = TestCacheEntries.readyEntry(cacheKey = "multi-key")
      val entry2 = TestCacheEntries.creatingEntry(cacheKey = "multi-key")

      for
        _       <- repo.create(entry1)
        _       <- repo.create(entry2)
        results <- repo.findByKey("multi-key", CacheScopeType.Global, None)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 entries, got ${results.length}")
          assert(results.forall(_.cacheKey == "multi-key"))
        }
      yield ()
    }
  }

  test("findByKey returns empty for non-existent key") {
    withRepo { repo =>
      repo.findByKey("non-existent-key", CacheScopeType.Global, None).map(results => assert(results.isEmpty))
    }
  }

  // --- updateStatus ---

  test("updateStatus changes cache entry status") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry()

      for
        id        <- repo.create(entry)
        _         <- repo.updateStatus(id, CacheStatus.Ready)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, CacheStatus.Ready)
        }
      yield ()
    }
  }

  test("updateStatus returns true when successful") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry()

      for
        id     <- repo.create(entry)
        result <- repo.updateStatus(id, CacheStatus.Ready)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStatus returns false when not found") {
    withRepo { repo =>
      repo.updateStatus(UUID.randomUUID(), CacheStatus.Ready).map(result => assertEquals(result, false))
    }
  }

  // --- updateContentHash ---

  test("updateContentHash sets content hash") {
    withRepo { repo =>
      val entry       = TestCacheEntries.creatingEntry()
      val contentHash = "a1b2c3d4" * 8

      for
        id        <- repo.create(entry)
        _         <- repo.updateContentHash(id, contentHash)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.contentHash, Some(contentHash))
        }
      yield ()
    }
  }

  test("updateContentHash returns false when not found") {
    withRepo { repo =>
      repo.updateContentHash(UUID.randomUUID(), "a" * 64).map(result => assertEquals(result, false))
    }
  }

  // --- recordAccess ---

  test("recordAccess increments access count") {
    withRepo { repo =>
      val entry = TestCacheEntries.readyEntry()

      for
        id <- repo.create(entry)

        _         <- repo.recordAccess(id)
        retrieved <- repo.findById(id)
        _         <- IO(assertEquals(retrieved.get.accessCount, 1))

        _         <- repo.recordAccess(id)
        retrieved <- repo.findById(id)
        _         <- IO(assertEquals(retrieved.get.accessCount, 2))
      yield ()
    }
  }

  test("recordAccess returns false when not found") {
    withRepo { repo =>
      repo.recordAccess(UUID.randomUUID()).map(result => assertEquals(result, false))
    }
  }

  // --- findExpired ---

  test("findExpired returns entries past their expiry date") {
    withRepo { repo =>
      val expired    = TestCacheEntries.expiredEntry(cacheKey = "expired-cache")
      val notExpired = TestCacheEntries.readyEntry(cacheKey = "active-cache")

      for
        expiredId    <- repo.create(expired)
        notExpiredId <- repo.create(notExpired)
        results      <- repo.findExpired()
        _ <- IO {
          assert(results.exists(_.cacheId == expiredId), "Should include expired entry")
          assert(!results.exists(_.cacheId == notExpiredId), "Should not include non-expired entry")
        }
      yield ()
    }
  }

  test("findExpired respects limit parameter") {
    withRepo { repo =>
      val entries = (1 to 10).map(i => TestCacheEntries.expiredEntry(cacheKey = s"expired-limit-$i"))

      for
        _       <- entries.toVector.traverse(repo.create(_))
        results <- repo.findExpired(limit = 5)
        _       <- IO(assert(results.length <= 5, s"Should return at most 5 entries, got ${results.length}"))
      yield ()
    }
  }

  // --- findLeastRecentlyAccessed ---

  test("findLeastRecentlyAccessed returns ready entries sorted by last access") {
    withRepo { repo =>
      val entry1 = TestCacheEntries.readyEntry(cacheKey = "lru-1")
      val entry2 = TestCacheEntries.readyEntry(cacheKey = "lru-2")

      for
        id1 <- repo.create(entry1)
        id2 <- repo.create(entry2)
        // Access entry2 so it's more recently accessed
        _       <- repo.recordAccess(id2)
        results <- repo.findLeastRecentlyAccessed(limit = 10)
        _ <- IO {
          assert(results.nonEmpty)
          // entry1 should appear before entry2 since it was accessed less recently
          val idx1 = results.indexWhere(_.cacheId == id1)
          val idx2 = results.indexWhere(_.cacheId == id2)
          if idx1 >= 0 && idx2 >= 0 then assert(idx1 < idx2, "Less recently accessed entry should come first")
        }
      yield ()
    }
  }

  test("findLeastRecentlyAccessed does not return non-ready entries") {
    withRepo { repo =>
      val creating = TestCacheEntries.creatingEntry(cacheKey = "lru-creating")
      val ready    = TestCacheEntries.readyEntry(cacheKey = "lru-ready")

      for
        creatingId <- repo.create(creating)
        readyId    <- repo.create(ready)
        results    <- repo.findLeastRecentlyAccessed(limit = 100)
        _ <- IO {
          assert(!results.exists(_.cacheId == creatingId), "Should not include creating entries")
          assert(results.exists(_.cacheId == readyId), "Should include ready entries")
        }
      yield ()
    }
  }

  // --- delete ---

  test("delete removes the cache entry") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry()

      for
        id     <- repo.create(entry)
        _      <- repo.delete(id)
        result <- repo.findById(id)
        _      <- IO(assert(result.isEmpty, "Deleted entry should not be found"))
      yield ()
    }
  }

  test("delete returns true when successful") {
    withRepo { repo =>
      val entry = TestCacheEntries.creatingEntry()

      for
        id     <- repo.create(entry)
        result <- repo.delete(id)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("delete returns false when not found") {
    withRepo { repo =>
      repo.delete(UUID.randomUUID()).map(result => assertEquals(result, false))
    }
  }

  // --- lifecycle test ---

  test("complete cache entry lifecycle") {
    withRepo { repo =>
      val entry       = TestCacheEntries.creatingEntry(cacheKey = "lifecycle-cache")
      val contentHash = "abcdef01" * 8

      for
        // Create in Creating status
        id        <- repo.create(entry)
        retrieved <- repo.findById(id)
        _ <- IO {
          assertEquals(retrieved.get.status, CacheStatus.Creating)
          assertEquals(retrieved.get.contentHash, None)
          assertEquals(retrieved.get.accessCount, 0)
        }

        // Set content hash
        _ <- repo.updateContentHash(id, contentHash)

        // Transition to Ready
        _         <- repo.updateStatus(id, CacheStatus.Ready)
        retrieved <- repo.findById(id)
        _ <- IO {
          assertEquals(retrieved.get.status, CacheStatus.Ready)
          assertEquals(retrieved.get.contentHash, Some(contentHash))
        }

        // Cache hit - lookup
        hit <- repo.findByKeyAndScope(
          cacheKey = "lifecycle-cache",
          scopeType = entry.scopeType,
          scopeValue = entry.scopeValue,
          pathsHash = entry.pathsHash,
          inputHash = entry.inputHash
        )
        _ <- IO(assert(hit.isDefined, "Should find ready entry"))

        // Record access
        _         <- repo.recordAccess(id)
        _         <- repo.recordAccess(id)
        retrieved <- repo.findById(id)
        _         <- IO(assertEquals(retrieved.get.accessCount, 2))

        // Mark for deletion
        _         <- repo.updateStatus(id, CacheStatus.Deleting)
        retrieved <- repo.findById(id)
        _         <- IO(assertEquals(retrieved.get.status, CacheStatus.Deleting))

        // Delete
        _       <- repo.delete(id)
        deleted <- repo.findById(id)
        _       <- IO(assert(deleted.isEmpty))
      yield ()
    }
  }
