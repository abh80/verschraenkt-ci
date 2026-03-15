package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.storage.db.codecs.Enums.SecretScope
import com.verschraenkt.ci.storage.fixtures.{ DatabaseContainerFixture, TestDatabaseFixture, TestSecrets }
import munit.CatsEffectSuite

import java.time.Instant
import java.util.UUID

class SecretRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private def withRepo[A](f: SecretRepository => IO[A]): IO[A] =
    withDatabase { db =>
      f(new SecretRepository(db))
    }

  // --- create ---

  test("create inserts secret and returns generated ID") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id     <- repo.create(secret)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.name, secret.name)
          assertEquals(result.get.scope, SecretScope.Global)
          assertEquals(result.get.isActive, true)
          assertEquals(result.get.version, 1)
        }
      yield ()
    }
  }

  test("create persists all fields correctly") {
    withRepo { repo =>
      val secret = TestSecrets.pipelineScopedSecret(name = "full-fields-secret", pipelineId = "my-pipeline")

      for
        id     <- repo.create(secret)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          val r = result.get
          assertEquals(r.name, "full-fields-secret")
          assertEquals(r.scope, SecretScope.Pipeline)
          assertEquals(r.scopeEntityId, Some("my-pipeline"))
          assertEquals(r.vaultPathKeyId, secret.vaultPathKeyId)
          assertEquals(r.createdBy, secret.createdBy)
        }
      yield ()
    }
  }

  // --- findById ---

  test("findById returns None when secret does not exist") {
    withRepo { repo =>
      repo.findById(UUID.randomUUID()).map(r => assert(r.isEmpty))
    }
  }

  test("findById does not return soft-deleted secrets") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id     <- repo.create(secret)
        _      <- repo.softDelete(id, "admin")
        result <- repo.findById(id)
        _      <- IO(assert(result.isEmpty, "Soft-deleted secret should not be found"))
      yield ()
    }
  }

  // --- findByNameAndScope ---

  test("findByNameAndScope returns matching active secret") {
    withRepo { repo =>
      val secret = TestSecrets.secret(name = "lookup-secret")

      for
        id <- repo.create(secret)
        result <- repo.findByNameAndScope(
          name = "lookup-secret",
          scope = SecretScope.Global,
          scopeEntityId = None
        )
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.secretId, Some(id))
        }
      yield ()
    }
  }

  test("findByNameAndScope returns None for inactive secret") {
    withRepo { repo =>
      val secret = TestSecrets.secret(name = "inactive-lookup")

      for
        id <- repo.create(secret)
        _  <- repo.deactivate(id)
        result <- repo.findByNameAndScope(
          name = "inactive-lookup",
          scope = SecretScope.Global,
          scopeEntityId = None
        )
        _ <- IO(assert(result.isEmpty, "Should not find inactive secret"))
      yield ()
    }
  }

  test("findByNameAndScope with pipeline scope") {
    withRepo { repo =>
      val secret = TestSecrets.pipelineScopedSecret(name = "pipeline-lookup", pipelineId = "pipe-1")

      for
        id <- repo.create(secret)
        result <- repo.findByNameAndScope(
          name = "pipeline-lookup",
          scope = SecretScope.Pipeline,
          scopeEntityId = Some("pipe-1")
        )
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.secretId, Some(id))
        }
      yield ()
    }
  }

  test("findByNameAndScope does not cross scope entities") {
    withRepo { repo =>
      val secret = TestSecrets.pipelineScopedSecret(name = "scope-cross", pipelineId = "pipe-a")

      for
        _ <- repo.create(secret)
        result <- repo.findByNameAndScope(
          name = "scope-cross",
          scope = SecretScope.Pipeline,
          scopeEntityId = Some("pipe-b")
        )
        _ <- IO(assert(result.isEmpty, "Should not find secret from different scope entity"))
      yield ()
    }
  }

  // --- findByScope ---

  test("findByScope returns all active secrets for a scope") {
    withRepo { repo =>
      val s1 = TestSecrets.secret(name = "scope-s1")
      val s2 = TestSecrets.secret(name = "scope-s2")

      for
        _       <- repo.create(s1)
        _       <- repo.create(s2)
        results <- repo.findByScope(SecretScope.Global, None)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 secrets, got ${results.length}")
          assert(results.forall(_.scope == SecretScope.Global))
          assert(results.forall(_.isActive))
        }
      yield ()
    }
  }

  test("findByScope returns empty for non-existent scope entity") {
    withRepo { repo =>
      repo
        .findByScope(SecretScope.Pipeline, Some("non-existent-pipeline"))
        .map(results => assert(results.isEmpty))
    }
  }

  // --- updateVaultPath ---

  test("updateVaultPath updates encrypted path and key ID") {
    withRepo { repo =>
      val secret   = TestSecrets.secret()
      val newPath  = "new/vault/path".getBytes("UTF-8")
      val newKeyId = "key-002"

      for
        id        <- repo.create(secret)
        _         <- repo.updateVaultPath(id, newPath, newKeyId)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assert(java.util.Arrays.equals(retrieved.get.vaultPathEncrypted, newPath))
          assertEquals(retrieved.get.vaultPathKeyId, newKeyId)
        }
      yield ()
    }
  }

  test("updateVaultPath returns false when not found") {
    withRepo { repo =>
      repo
        .updateVaultPath(UUID.randomUUID(), "x".getBytes, "key-x")
        .map(result => assertEquals(result, false))
    }
  }

  // --- rotate ---

  test("rotate bumps version and updates rotatedAt") {
    withRepo { repo =>
      val secret  = TestSecrets.secret()
      val newPath = "rotated/vault/path".getBytes("UTF-8")

      for
        id        <- repo.create(secret)
        _         <- repo.rotate(id, newPath, "key-003")
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.version, 2)
          assert(retrieved.get.rotatedAt.isDefined)
          assertEquals(retrieved.get.vaultPathKeyId, "key-003")
        }
      yield ()
    }
  }

  test("rotate returns false when not found") {
    withRepo { repo =>
      repo
        .rotate(UUID.randomUUID(), "x".getBytes, "key-x")
        .map(result => assertEquals(result, false))
    }
  }

  // --- deactivate ---

  test("deactivate sets isActive to false") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id        <- repo.create(secret)
        _         <- repo.deactivate(id)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.isActive, false)
        }
      yield ()
    }
  }

  test("deactivate returns true when successful") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id     <- repo.create(secret)
        result <- repo.deactivate(id)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("deactivate returns false when not found") {
    withRepo { repo =>
      repo.deactivate(UUID.randomUUID()).map(result => assertEquals(result, false))
    }
  }

  // --- softDelete ---

  test("softDelete marks secret as deleted") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id     <- repo.create(secret)
        _      <- repo.softDelete(id, "admin-user")
        result <- repo.findById(id)
        _      <- IO(assert(result.isEmpty, "Soft-deleted secret should not be found by findById"))
      yield ()
    }
  }

  test("softDelete returns true when successful") {
    withRepo { repo =>
      val secret = TestSecrets.secret()

      for
        id     <- repo.create(secret)
        result <- repo.softDelete(id, "admin-user")
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("softDelete returns false when not found") {
    withRepo { repo =>
      repo.softDelete(UUID.randomUUID(), "admin").map(result => assertEquals(result, false))
    }
  }

  // --- findDueForRotation ---

  test("findDueForRotation returns secrets not rotated recently") {
    withRepo { repo =>
      val oldSecret = TestSecrets.rotatedSecret(name = "old-rotation")
      val newSecret = TestSecrets.secret(name = "no-rotation")

      for
        oldId   <- repo.create(oldSecret)
        newId   <- repo.create(newSecret)
        results <- repo.findDueForRotation(before = Instant.now())
        _ <- IO {
          assert(results.exists(_.secretId.contains(oldId)), "Should include secret with old rotation")
          assert(results.exists(_.secretId.contains(newId)), "Should include secret never rotated")
        }
      yield ()
    }
  }

  test("findDueForRotation respects limit parameter") {
    withRepo { repo =>
      val secrets = (1 to 10).map(i => TestSecrets.secret(name = s"rotation-limit-$i"))

      for
        _       <- secrets.toVector.foldLeft(IO.unit)((acc, s) => acc *> repo.create(s).void)
        results <- repo.findDueForRotation(before = Instant.now(), limit = 5)
        _       <- IO(assert(results.length <= 5, s"Should return at most 5 secrets, got ${results.length}"))
      yield ()
    }
  }

  // --- lifecycle test ---

  test("complete secret lifecycle") {
    withRepo { repo =>
      val secret  = TestSecrets.secret(name = "lifecycle-secret")
      val newPath = "rotated/path".getBytes("UTF-8")

      for
        // Create
        id        <- repo.create(secret)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.isActive, true)
          assertEquals(retrieved.get.version, 1)
          assertEquals(retrieved.get.rotatedAt, None)
        }

        // Lookup by name
        found <- repo.findByNameAndScope("lifecycle-secret", SecretScope.Global, None)
        _     <- IO(assert(found.isDefined, "Should find by name and scope"))

        // Rotate
        _         <- repo.rotate(id, newPath, "key-002")
        retrieved <- repo.findById(id)
        _ <- IO {
          assertEquals(retrieved.get.version, 2)
          assert(retrieved.get.rotatedAt.isDefined)
        }

        // Deactivate
        _         <- repo.deactivate(id)
        retrieved <- repo.findById(id)
        _         <- IO(assertEquals(retrieved.get.isActive, false))

        // Should not be found by name lookup anymore
        notFound <- repo.findByNameAndScope("lifecycle-secret", SecretScope.Global, None)
        _        <- IO(assert(notFound.isEmpty, "Deactivated secret should not be found by name"))

        // Soft delete
        _       <- repo.softDelete(id, "admin")
        deleted <- repo.findById(id)
        _       <- IO(assert(deleted.isEmpty))
      yield ()
    }
  }
