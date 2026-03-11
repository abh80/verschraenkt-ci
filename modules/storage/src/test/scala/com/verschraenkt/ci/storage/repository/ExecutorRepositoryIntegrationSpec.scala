package com.verschraenkt.ci.storage.repository

import cats.effect.{ IO, Resource }
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.engine.api.ExecutorId
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutorStatus
import com.verschraenkt.ci.storage.db.tables.ExecutorRow
import com.verschraenkt.ci.storage.fixtures.{ DatabaseContainerFixture, TestDatabaseFixture, TestExecutors }
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExecutorRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val dbTimestampToleranceNanos = 1000L

  private def assertInstantWithinTolerance(
      actual: Instant,
      expected: Instant,
      toleranceNanos: Long = dbTimestampToleranceNanos
  ): Unit =
    val diff = Math.abs(java.time.Duration.between(expected, actual).toNanos)
    assert(
      diff <= toleranceNanos,
      s"Expected ${expected.toString} within ${toleranceNanos}ns, but got ${actual.toString} (diff=${diff}ns)"
    )

  private def withRepo[A](f: ExecutorRepository => IO[A]): IO[A] =
    withDatabase { db => f(new ExecutorRepository(db)) }

  private def savedExecutorFixture(executor: ExecutorRow = TestExecutors.onlineExecutor()) =
    ResourceFunFixture(
      Resource.make(
        withRepo { repo =>
          repo.save(executor).map(id => executor.copy(executorId = Some(id)))
        }
      )(_ => IO.unit)
    )

  // --- findById ---

  savedExecutorFixture().test("findById returns executor when it exists") { saved =>
    withRepo { repo =>
      for
        result <- repo.findById(ExecutorId(saved.executorId.get))
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.executorId, saved.executorId)
          assertEquals(result.get.name, saved.name)
        }
      yield ()
    }
  }

  test("findById returns None when executor does not exist") {
    withRepo { repo =>
      repo.findById(ExecutorId(UUID.randomUUID())).map(r => assert(r.isEmpty))
    }
  }

  savedExecutorFixture().test("findById excludes soft-deleted executors") { saved =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(ExecutorId(saved.executorId.get))
        result <- repo.findById(ExecutorId(saved.executorId.get))
        _      <- IO(assert(result.isEmpty))
      yield ()
    }
  }

  // --- save ---

  test("save inserts executor and returns row with generated ID") {
    withRepo { repo =>
      val executor = TestExecutors.newExecutor()

      for
        saved <- repo.save(executor).map(id => executor.copy(executorId = Some(id)))
        _ <- IO {
          assert(saved.executorId.isDefined, "Should have auto-generated ID")
          assertEquals(saved.name, executor.name)
          assertEquals(saved.platform, executor.platform)
        }
      yield ()
    }
  }

  test("save persists all fields correctly") {
    withDatabase { db =>
      val repo     = new ExecutorRepository(db)
      val executor = TestExecutors.onlineExecutor()

      for
        saved     <- repo.save(executor).map(id => executor.copy(executorId = Some(id)))
        retrieved <- repo.findById(ExecutorId(saved.executorId.get))
        _ <- IO {
          assert(retrieved.isDefined)
          val r = retrieved.get
          assertEquals(r.name, executor.name)
          assertEquals(r.hostname, executor.hostname)
          assertEquals(r.platform, executor.platform)
          assertEquals(r.architecture, executor.architecture)
          assertEquals(r.cpuMillis, executor.cpuMillis)
          assertEquals(r.memoryMibs, executor.memoryMibs)
          assertEquals(r.gpuCount, executor.gpuCount)
          assertEquals(r.diskMibs, executor.diskMibs)
          assertEquals(r.labels, executor.labels)
          assertEquals(r.status, executor.status)
          assertEquals(r.tokenHash, executor.tokenHash)
          assertEquals(r.version, executor.version)
        }
      yield ()
    }
  }

  test("save multiple different executors") {
    withRepo { repo =>
      val exec1 = TestExecutors.newExecutor("exec-a")
      val exec2 = TestExecutors.newExecutor("exec-b")
      val exec3 = TestExecutors.newExecutor("exec-c")

      for
        saved1 <- repo.save(exec1)
        saved2 <- repo.save(exec2)
        saved3 <- repo.save(exec3)
        _ <- IO {
          assert(saved1 != saved2)
          assert(saved2 != saved3)
        }
      yield ()
    }
  }

  // --- findByStatus ---

  test("findByStatus returns only executors with matching status") {
    withRepo { repo =>
      val online   = TestExecutors.onlineExecutor("status-online")
      val offline  = TestExecutors.offlineExecutor("status-offline")
      val draining = TestExecutors.drainingExecutor("status-draining")

      for
        savedOnline   <- repo.save(online)
        savedOffline  <- repo.save(offline)
        savedDraining <- repo.save(draining)
        results       <- repo.findByStatus(ExecutorStatus.Online, 100)
        _ <- IO {
          assert(results.exists(_.executorId.get == savedOnline))
          assert(!results.exists(_.executorId.get == savedOffline))
          assert(!results.exists(_.executorId.get == savedDraining))
        }
      yield ()
    }
  }

  test("findByStatus excludes soft-deleted executors") {
    withRepo { repo =>
      val exec1 = TestExecutors.onlineExecutor("status-del-1")
      val exec2 = TestExecutors.onlineExecutor("status-del-2")

      for
        saved1  <- repo.save(exec1)
        saved2  <- repo.save(exec2)
        _       <- repo.softDelete(ExecutorId(saved1))
        results <- repo.findByStatus(ExecutorStatus.Online, 100)
        _ <- IO {
          assert(!results.exists(_.executorId.get == saved1), "Should not include soft-deleted")
          assert(results.exists(_.executorId.get == saved2), "Should include active")
        }
      yield ()
    }
  }

  test("findByStatus respects limit parameter") {
    withRepo { repo =>
      val executors = (1 to 10).map(i => TestExecutors.onlineExecutor(s"limit-status-$i"))

      for
        _       <- executors.toVector.traverse(repo.save)
        results <- repo.findByStatus(ExecutorStatus.Online, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executors, got ${results.length}"))
      yield ()
    }
  }

  // --- findActive ---

  test("findActive returns non-deleted executors") {
    withRepo { repo =>
      val exec1 = TestExecutors.onlineExecutor("active-1")
      val exec2 = TestExecutors.offlineExecutor("active-2")

      for
        saved1  <- repo.save(exec1)
        saved2  <- repo.save(exec2)
        results <- repo.findActive(None, 100)
        _ <- IO {
          assert(results.exists(_.executorId.get == saved1))
          assert(results.exists(_.executorId.get == saved2))
        }
      yield ()
    }
  }

  test("findActive excludes soft-deleted executors") {
    withRepo { repo =>
      val exec1 = TestExecutors.onlineExecutor("active-del-1")
      val exec2 = TestExecutors.onlineExecutor("active-del-2")

      for
        saved1  <- repo.save(exec1)
        saved2  <- repo.save(exec2)
        _       <- repo.softDelete(ExecutorId(saved1))
        results <- repo.findActive(None, 100)
        _ <- IO {
          assert(!results.exists(_.executorId.get == saved1))
          assert(results.exists(_.executorId.get == saved2))
        }
      yield ()
    }
  }

  test("findActive filters by labels") {
    withRepo { repo =>
      val gpuExecutor = TestExecutors
        .drainingExecutor("label-gpu")
        .copy(
          status = ExecutorStatus.Online,
          labels = List("linux", "gpu", "high-memory")
        )
      val plainExecutor = TestExecutors
        .onlineExecutor("label-plain")
        .copy(
          labels = List("linux", "general")
        )

      for
        savedGpu   <- repo.save(gpuExecutor)
        savedPlain <- repo.save(plainExecutor)
        results    <- repo.findActive(Some(Set("gpu")), 100)
        _ <- IO {
          assert(
            results.exists(_.executorId.get == savedGpu),
            "Should include executor with gpu label"
          )
          assert(
            !results.exists(_.executorId.get == savedPlain),
            "Should not include executor without gpu label"
          )
        }
      yield ()
    }
  }

  test("findActive with multiple labels requires all labels present") {
    withRepo { repo =>
      val fullLabels = TestExecutors
        .onlineExecutor("multi-label-full")
        .copy(
          labels = List("linux", "gpu", "high-memory")
        )
      val partialLabels = TestExecutors
        .onlineExecutor("multi-label-partial")
        .copy(
          labels = List("linux", "gpu")
        )

      for
        savedFull    <- repo.save(fullLabels)
        savedPartial <- repo.save(partialLabels)
        results      <- repo.findActive(Some(Set("gpu", "high-memory")), 100)
        _ <- IO {
          assert(
            results.exists(_.executorId.get == savedFull),
            "Should include executor with all labels"
          )
          assert(
            !results.exists(_.executorId.get == savedPartial),
            "Should not include executor missing a label"
          )
        }
      yield ()
    }
  }

  test("findActive with no labels returns all non-deleted") {
    withRepo { repo =>
      val exec1 = TestExecutors.onlineExecutor("no-label-1")
      val exec2 = TestExecutors.offlineExecutor("no-label-2")

      for
        saved1  <- repo.save(exec1)
        saved2  <- repo.save(exec2)
        results <- repo.findActive(None, 100)
        _ <- IO {
          assert(results.exists(_.executorId.get == saved1))
          assert(results.exists(_.executorId.get == saved2))
        }
      yield ()
    }
  }

  test("findActive respects limit parameter") {
    withRepo { repo =>
      val executors = (1 to 10).map(i => TestExecutors.onlineExecutor(s"limit-active-$i"))

      for
        _       <- executors.toVector.traverse(repo.save)
        results <- repo.findActive(None, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executors, got ${results.length}"))
      yield ()
    }
  }

  // --- updateStatus ---

  savedExecutorFixture().test("updateStatus changes executor status") { saved =>
    withRepo { repo =>
      for
        _         <- repo.updateStatus(ExecutorId(saved.executorId.get), ExecutorStatus.Draining)
        retrieved <- repo.findById(ExecutorId(saved.executorId.get))
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutorStatus.Draining)
        }
      yield ()
    }
  }

  savedExecutorFixture().test("updateStatus returns true when successful") { saved =>
    withRepo { repo =>
      for
        result <- repo.updateStatus(ExecutorId(saved.executorId.get), ExecutorStatus.Offline)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStatus returns false when executor not found") {
    withRepo { repo =>
      repo.updateStatus(ExecutorId(UUID.randomUUID()), ExecutorStatus.Online).map { result =>
        assertEquals(result, false)
      }
    }
  }

  savedExecutorFixture().test("updateStatus does not update soft-deleted executors") { saved =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(ExecutorId(saved.executorId.get))
        result <- repo.updateStatus(ExecutorId(saved.executorId.get), ExecutorStatus.Online)
        _      <- IO(assertEquals(result, false))
      yield ()
    }
  }

  // --- updateHeartbeat ---

  savedExecutorFixture().test("updateHeartbeat sets heartbeat time") { saved =>
    withRepo { repo =>
      val heartbeatTime = Instant.now()

      for
        _         <- repo.updateHeartbeat(ExecutorId(saved.executorId.get), heartbeatTime)
        retrieved <- repo.findById(ExecutorId(saved.executorId.get))
        _ <- IO {
          assert(retrieved.isDefined)
          assertInstantWithinTolerance(
            retrieved.get.lastHeartbeat,
            heartbeatTime.truncatedTo(ChronoUnit.MICROS)
          )
        }
      yield ()
    }
  }

  savedExecutorFixture().test("updateHeartbeat returns true when successful") { saved =>
    withRepo { repo =>
      for
        result <- repo.updateHeartbeat(ExecutorId(saved.executorId.get), Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateHeartbeat returns false when executor not found") {
    withRepo { repo =>
      repo.updateHeartbeat(ExecutorId(UUID.randomUUID()), Instant.now()).map { result =>
        assertEquals(result, false)
      }
    }
  }

  savedExecutorFixture().test("updateHeartbeat does not update soft-deleted executors") { saved =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(ExecutorId(saved.executorId.get))
        result <- repo.updateHeartbeat(ExecutorId(saved.executorId.get), Instant.now())
        _      <- IO(assertEquals(result, false))
      yield ()
    }
  }

  // --- softDelete ---

  savedExecutorFixture().test("softDelete marks executor as deleted") { saved =>
    withDatabase { db =>
      val repo = new ExecutorRepository(db)

      for
        _ <- repo.softDelete(ExecutorId(saved.executorId.get))
        deletedAt <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT deleted_at FROM executors WHERE executor_id = ?"
            )
            stmt.setObject(1, saved.executorId.get)
            val rs = stmt.executeQuery()
            val ts = if rs.next() then Option(rs.getTimestamp("deleted_at")) else None
            rs.close()
            stmt.close()
            ts
          finally conn.close()
        }
        _ <- IO(assert(deletedAt.isDefined, "deleted_at should be set"))
      yield ()
    }
  }

  savedExecutorFixture().test("softDelete sets deleted_at timestamp within expected range") { saved =>
    withDatabase { db =>
      val repo         = new ExecutorRepository(db)
      val beforeDelete = Instant.now()

      for
        _ <- repo.softDelete(ExecutorId(saved.executorId.get))
        _ <- IO.sleep(scala.concurrent.duration.Duration(10, "ms"))
        afterDelete = Instant.now()
        deletedAt <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT deleted_at FROM executors WHERE executor_id = ?"
            )
            stmt.setObject(1, saved.executorId.get)
            val rs = stmt.executeQuery()
            val ts = if rs.next() then rs.getTimestamp("deleted_at").toInstant else fail("Not found")
            rs.close()
            stmt.close()
            ts
          finally conn.close()
        }
        _ <- IO {
          assert(!deletedAt.isBefore(beforeDelete), "deleted_at should be after delete call")
          assert(!deletedAt.isAfter(afterDelete), "deleted_at should be before current time")
        }
      yield ()
    }
  }

  savedExecutorFixture().test("softDelete returns true when successful") { saved =>
    withRepo { repo =>
      for
        result <- repo.softDelete(ExecutorId(saved.executorId.get))
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("softDelete returns false when executor not found") {
    withRepo { repo =>
      repo.softDelete(ExecutorId(UUID.randomUUID())).map { result =>
        assertEquals(result, false)
      }
    }
  }

  savedExecutorFixture().test("softDelete makes executor invisible to findById") { saved =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(ExecutorId(saved.executorId.get))
        result <- repo.findById(ExecutorId(saved.executorId.get))
        _      <- IO(assert(result.isEmpty, "Soft-deleted executor should not be found by findById"))
      yield ()
    }
  }

  savedExecutorFixture().test("softDelete cannot be performed multiple times on same executor") { saved =>
    withRepo { repo =>
      for
        result1 <- repo.softDelete(ExecutorId(saved.executorId.get))
        result2 <- repo.softDelete(ExecutorId(saved.executorId.get))
        _ <- IO {
          assertEquals(result1, true, "First softDelete should succeed")
          assertEquals(result2, false, "Second softDelete should not succeed")
        }
      yield ()
    }
  }

  // --- lifecycle test ---

  test("complete executor lifecycle") {
    withRepo { repo =>
      val executor      = TestExecutors.newExecutor("lifecycle-exec")
      val heartbeatTime = Instant.now()

      for
        // Register executor
        saved <- repo.save(executor)
        _ <- IO {
          assert(saved.toString.nonEmpty)
        }

        // Find by ID
        retrieved <- repo.findById(ExecutorId(saved))
        _         <- IO(assert(retrieved.isDefined))

        // Update heartbeat
        _         <- repo.updateHeartbeat(ExecutorId(saved), heartbeatTime)
        retrieved <- repo.findById(ExecutorId(saved))
        _ <- IO {
          assertInstantWithinTolerance(
            retrieved.get.lastHeartbeat,
            heartbeatTime.truncatedTo(ChronoUnit.MICROS)
          )
        }

        // Drain executor
        _         <- repo.updateStatus(ExecutorId(saved), ExecutorStatus.Draining)
        retrieved <- repo.findById(ExecutorId(saved))
        _         <- IO(assertEquals(retrieved.get.status, ExecutorStatus.Draining))

        // Set offline
        _         <- repo.updateStatus(ExecutorId(saved), ExecutorStatus.Offline)
        retrieved <- repo.findById(ExecutorId(saved))
        _         <- IO(assertEquals(retrieved.get.status, ExecutorStatus.Offline))

        // Decommission (soft delete)
        _      <- repo.softDelete(ExecutorId(saved))
        result <- repo.findById(ExecutorId(saved))
        _      <- IO(assert(result.isEmpty, "Soft-deleted executor should not be found"))
      yield ()
    }
  }
