package com.verschraenkt.ci.storage.repository

import cats.effect.{ IO, Resource }
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.ExecutionRow
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.fixtures.{ DatabaseContainerFixture, TestDatabaseFixture, TestExecutions }
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** Integration tests for ExecutionRepository with real PostgreSQL database
  *
  * These tests use Testcontainers to spin up a real PostgreSQL instance
  */
class ExecutionRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  // Enable auto-migration for all tests
  autoMigrate(true)
  sharing(true)

  private val seededPipelineIds: Vector[String] =
    Vector("test-pipeline") ++ (1 to 500).map(i => s"pipeline-$i")

  private def seedPipelineVersions(db: DatabaseModule): IO[Unit] =
    IO.blocking {
      val conn = db.database.source.createConnection()
      try
        val pipelineSql =
          """INSERT INTO pipelines (pipeline_id, name, definition, created_by)
             VALUES (?, ?, '{"workflows":[]}'::jsonb, 'test-user')
             ON CONFLICT (pipeline_id) DO NOTHING"""

        val versionSql =
          """INSERT INTO pipeline_versions (pipeline_id, version, definition, created_by, change_summary)
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for execution integration tests')
             ON CONFLICT (pipeline_id, version) DO NOTHING"""

        val pipelineStmt = conn.prepareStatement(pipelineSql)
        val versionStmt  = conn.prepareStatement(versionSql)
        try
          seededPipelineIds.foreach { pipelineId =>
            pipelineStmt.setString(1, pipelineId)
            pipelineStmt.setString(2, s"Pipeline $pipelineId")
            pipelineStmt.addBatch()

            versionStmt.setString(1, pipelineId)
            versionStmt.addBatch()
          }
          pipelineStmt.executeBatch()
          versionStmt.executeBatch(): Unit
        finally
          pipelineStmt.close()
          versionStmt.close()
      finally conn.close()
    }

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  // Helper to provide repository instance
  private def withRepo[A](f: ExecutionRepository => IO[A]): IO[A] =
    withSeededDatabase { db => f(new ExecutionRepository(db)) }

  // Use this function to create a saved execution before the test run
  private def savedExecutionFixture(execution: ExecutionRow = TestExecutions.queuedExecution()) =
    ResourceFunFixture(
      Resource.make(
        withRepo { repo =>
          repo.create(execution).map(_ => execution)
        }
      )(_ => IO.unit)
    )

  savedExecutionFixture().test("findById returns execution when it exists") { execution =>
    withRepo { repo =>
      for
        result <- repo.findById(execution.executionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.executionId, execution.executionId)
        }
      yield ()
    }
  }

  test("findById returns None when execution does not exist") {
    withRepo { repo =>
      val nonExistentId = UUID.randomUUID()
      repo.findById(nonExistentId).map(p => assert(p.isEmpty))
    }
  }

  savedExecutionFixture().test("findById excludes soft-deleted executions") { execution =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        result <- repo.findById(execution.executionId)
        _      <- IO(assert(result.isEmpty))
      yield ()
    }
  }

  savedExecutionFixture().test("findByIdIncludingDeleted returns active execution") { execution =>
    withRepo { repo =>
      for
        result <- repo.findByIdIncludingDeleted(execution.executionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.executionId, execution.executionId)
        }
      yield ()
    }
  }

  savedExecutionFixture().test("findByIdIncludingDeleted returns soft-deleted execution") { execution =>
    withRepo { repo =>
      for
        _      <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        result <- repo.findByIdIncludingDeleted(execution.executionId)
        _ <- IO {
          assert(result.isDefined, "Should find soft-deleted execution")
          assertEquals(result.get.executionId, execution.executionId)
        }
      yield ()
    }
  }

  test("create inserts execution into database") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        executionId <- repo.create(execution)
        _           <- IO(assertEquals(executionId, execution.executionId))
      yield ()
    }
  }

  test("create with duplicate ID raises DuplicateKey error") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _     <- repo.create(execution)
        error <- repo.create(execution).intercept[StorageError.DuplicateKey]
        _ <- IO {
          assertEquals(error.entity, "Execution")
          assertEquals(error.id, execution.executionId.toString)
        }
      yield ()
    }
  }

  test("create persists all metadata correctly") {
    withSeededDatabase { db =>
      val repo      = new ExecutionRepository(db)
      val execution = TestExecutions.runningExecution()

      for
        _ <- repo.create(execution)
        _ <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              """SELECT status, trigger, trigger_by, pipeline_version, timeout_at
                 FROM executions WHERE execution_id = ?"""
            )
            stmt.setObject(1, execution.executionId)
            val rs = stmt.executeQuery()
            if rs.next() then
              assertEquals(rs.getString("status"), "running")
              assertEquals(rs.getString("trigger"), "manual")
              assertEquals(rs.getString("trigger_by"), TestExecutions.testUser.unwrap)
              assertEquals(rs.getInt("pipeline_version"), 1)
              assert(rs.getTimestamp("timeout_at") != null)
            else fail("Execution not found in database")
            rs.close()
            stmt.close()
          finally conn.close()
        }
      yield ()
    }
  }

  test("findByPipelineId returns executions for pipeline") {
    withRepo { repo =>
      val pipelineId = PipelineId("test-pipeline")
      val exec1      = TestExecutions.queuedExecution(pipelineId)
      val exec2      = TestExecutions.runningExecution(pipelineId)
      val exec3      = TestExecutions.completedExecution(pipelineId)

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        results <- repo.findByPipelineId(pipelineId, None, 100)
        _ <- IO {
          assert(results.length >= 3)
          assert(results.exists(_.executionId == exec1.executionId))
          assert(results.exists(_.executionId == exec2.executionId))
          assert(results.exists(_.executionId == exec3.executionId))
        }
      yield ()
    }
  }

  test("findByPipelineId filters by status") {
    withRepo { repo =>
      val pipelineId = PipelineId("test-pipeline")
      val exec1      = TestExecutions.queuedExecution(pipelineId)
      val exec2      = TestExecutions.runningExecution(pipelineId)
      val exec3      = TestExecutions.completedExecution(pipelineId)

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        results <- repo.findByPipelineId(pipelineId, Some(ExecutionStatus.Running), 100)
        _ <- IO {
          assert(results.exists(_.executionId == exec2.executionId))
          assert(!results.exists(_.executionId == exec1.executionId))
          assert(!results.exists(_.executionId == exec3.executionId))
        }
      yield ()
    }
  }

  test("findByPipelineId respects limit parameter") {
    withRepo { repo =>
      val pipelineId = PipelineId("test-pipeline")
      val executions = (1 to 10).map(_ => TestExecutions.queuedExecution(pipelineId))

      for
        _       <- executions.toVector.traverse(repo.create(_))
        results <- repo.findByPipelineId(pipelineId, None, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executions, got ${results.length}"))
      yield ()
    }
  }

  test("findByPipelineId excludes soft-deleted executions") {
    withRepo { repo =>
      val pipelineId = PipelineId("test-pipeline")
      val exec1      = TestExecutions.queuedExecution(pipelineId)
      val exec2      = TestExecutions.runningExecution(pipelineId)

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.softDelete(exec1.executionId, TestExecutions.testUser)
        results <- repo.findByPipelineId(pipelineId, None, 100)
        _ <- IO {
          assert(!results.exists(_.executionId == exec1.executionId), "Should NOT include soft-deleted exec1")
          assert(results.exists(_.executionId == exec2.executionId), "Should include active exec2")
        }
      yield ()
    }
  }

  test("findQueued returns only queued executions") {
    withRepo { repo =>
      val exec1 = TestExecutions.queuedExecution()
      val exec2 = TestExecutions.runningExecution()
      val exec3 = TestExecutions.queuedExecution()

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        results <- repo.findQueued(100)
        _ <- IO {
          assert(results.exists(_.executionId == exec1.executionId))
          assert(!results.exists(_.executionId == exec2.executionId))
          assert(results.exists(_.executionId == exec3.executionId))
        }
      yield ()
    }
  }

  test("findQueued respects limit parameter") {
    withRepo { repo =>
      val executions = (1 to 10).map(_ => TestExecutions.queuedExecution())

      for
        _       <- executions.toVector.traverse(repo.create(_))
        results <- repo.findQueued(5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executions, got ${results.length}"))
      yield ()
    }
  }

  test("findQueued sorts by queuedAt ascending") {
    withRepo { repo =>
      val now   = Instant.now()
      val exec1 = TestExecutions.queuedExecution().copy(queuedAt = now.minusSeconds(300))
      val exec2 = TestExecutions.queuedExecution().copy(queuedAt = now.minusSeconds(200))
      val exec3 = TestExecutions.queuedExecution().copy(queuedAt = now.minusSeconds(100))

      for
        _       <- repo.create(exec2) // Insert out of order
        _       <- repo.create(exec1)
        _       <- repo.create(exec3)
        results <- repo.findQueued(100)
        relevantResults = results.filter(e =>
          e.executionId == exec1.executionId || e.executionId == exec2.executionId || e.executionId == exec3.executionId
        )
        _ <- IO {
          assert(relevantResults.length >= 3)
          // exec1 should come first (oldest queued)
          val positions = Map(
            exec1.executionId -> relevantResults.indexWhere(_.executionId == exec1.executionId),
            exec2.executionId -> relevantResults.indexWhere(_.executionId == exec2.executionId),
            exec3.executionId -> relevantResults.indexWhere(_.executionId == exec3.executionId)
          )
          assert(
            positions(exec1.executionId) < positions(exec2.executionId),
            "exec1 (oldest) should come before exec2"
          )
          assert(
            positions(exec2.executionId) < positions(exec3.executionId),
            "exec2 should come before exec3 (newest)"
          )
        }
      yield ()
    }
  }

  test("findRunning returns only running executions") {
    withRepo { repo =>
      val exec1 = TestExecutions.queuedExecution()
      val exec2 = TestExecutions.runningExecution()
      val exec3 = TestExecutions.completedExecution()
      val exec4 = TestExecutions.runningExecution()

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        _       <- repo.create(exec4)
        results <- repo.findRunning(100)
        _ <- IO {
          assert(!results.exists(_.executionId == exec1.executionId))
          assert(results.exists(_.executionId == exec2.executionId))
          assert(!results.exists(_.executionId == exec3.executionId))
          assert(results.exists(_.executionId == exec4.executionId))
        }
      yield ()
    }
  }

  test("findRunning respects limit parameter") {
    withRepo { repo =>
      val executions = (1 to 10).map(_ => TestExecutions.runningExecution())

      for
        _       <- executions.toVector.traverse(repo.create(_))
        results <- repo.findRunning(5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executions, got ${results.length}"))
      yield ()
    }
  }

  test("findByConcurrencyGroup returns executions in group") {
    withRepo { repo =>
      val exec1 = TestExecutions.executionWithConcurrency(group = "group-a", position = 1)
      val exec2 = TestExecutions.executionWithConcurrency(group = "group-a", position = 2)
      val exec3 = TestExecutions.executionWithConcurrency(group = "group-b", position = 1)

      for
        _       <- repo.create(exec1)
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        results <- repo.findByConcurrencyGroup("group-a", 100)
        _ <- IO {
          assert(results.exists(_.executionId == exec1.executionId))
          assert(results.exists(_.executionId == exec2.executionId))
          assert(!results.exists(_.executionId == exec3.executionId))
        }
      yield ()
    }
  }

  test("findByConcurrencyGroup sorts by queue position") {
    withRepo { repo =>
      val exec1 = TestExecutions.executionWithConcurrency(group = "group-a", position = 3)
      val exec2 = TestExecutions.executionWithConcurrency(group = "group-a", position = 1)
      val exec3 = TestExecutions.executionWithConcurrency(group = "group-a", position = 2)

      for
        _       <- repo.create(exec1) // Insert out of order
        _       <- repo.create(exec2)
        _       <- repo.create(exec3)
        results <- repo.findByConcurrencyGroup("group-a", 100)
        relevantResults = results.filter(e =>
          e.executionId == exec1.executionId || e.executionId == exec2.executionId || e.executionId == exec3.executionId
        )
        _ <- IO {
          assert(relevantResults.length >= 3)
          // exec2 (position 1) should come first
          val positions = Map(
            exec1.executionId -> relevantResults.indexWhere(_.executionId == exec1.executionId),
            exec2.executionId -> relevantResults.indexWhere(_.executionId == exec2.executionId),
            exec3.executionId -> relevantResults.indexWhere(_.executionId == exec3.executionId)
          )
          assert(
            positions(exec2.executionId) < positions(exec3.executionId),
            "exec2 (pos 1) should come before exec3 (pos 2)"
          )
          assert(
            positions(exec3.executionId) < positions(exec1.executionId),
            "exec3 (pos 2) should come before exec1 (pos 3)"
          )
        }
      yield ()
    }
  }

  test("findByIdempotencyKey returns execution when key exists") {
    withRepo { repo =>
      val execution = TestExecutions.withIdempotencyKey("unique-key-123")

      for
        _      <- repo.create(execution)
        result <- repo.findByIdempotencyKey("unique-key-123")
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.executionId, execution.executionId)
        }
      yield ()
    }
  }

  test("findByIdempotencyKey returns None when key does not exist") {
    withRepo { repo =>
      repo.findByIdempotencyKey("non-existent-key").map(result => assert(result.isEmpty))
    }
  }

  test("findByIdempotencyKey excludes soft-deleted executions") {
    withRepo { repo =>
      val execution = TestExecutions.withIdempotencyKey("unique-key-456")

      for
        _      <- repo.create(execution)
        _      <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        result <- repo.findByIdempotencyKey("unique-key-456")
        _      <- IO(assert(result.isEmpty, "Should not find soft-deleted execution"))
      yield ()
    }
  }

  test("updateStatus changes execution status") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _         <- repo.create(execution)
        _         <- repo.updateStatus(execution.executionId, ExecutionStatus.Running, None)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("updateStatus with error message") {
    withRepo { repo =>
      val execution = TestExecutions.runningExecution()

      for
        _         <- repo.create(execution)
        _         <- repo.updateStatus(execution.executionId, ExecutionStatus.Failed, Some("Test error"))
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Failed)
          assertEquals(retrieved.get.errorMessage, Some("Test error"))
        }
      yield ()
    }
  }

  test("updateStatus returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _      <- repo.create(execution)
        result <- repo.updateStatus(execution.executionId, ExecutionStatus.Running, None)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStatus returns false when execution not found") {
    withRepo { repo =>
      val nonExistentId = UUID.randomUUID()
      repo.updateStatus(nonExistentId, ExecutionStatus.Running, None).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateStatus does not update soft-deleted executions") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _      <- repo.create(execution)
        _      <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        result <- repo.updateStatus(execution.executionId, ExecutionStatus.Running, None)
        _      <- IO(assertEquals(result, false))
      yield ()
    }
  }

  test("updateStartedAt sets started time") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()
      val startTime = Instant.now()

      for
        _         <- repo.create(execution)
        _         <- repo.updateStartedAt(execution.executionId, startTime)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          ) // due to database lack of precision we only check upto nanoseconds; who cares
        }
      yield ()
    }
  }

  test("updateStartedAt returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _      <- repo.create(execution)
        result <- repo.updateStartedAt(execution.executionId, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStartedAt returns false when execution not found") {
    withRepo { repo =>
      val nonExistentId = UUID.randomUUID()
      repo.updateStartedAt(nonExistentId, Instant.now()).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateCompletedAt sets completed time") {
    withRepo { repo =>
      val execution     = TestExecutions.runningExecution()
      val completedTime = Instant.now()

      for
        _         <- repo.create(execution)
        _         <- repo.updateCompletedAt(execution.executionId, completedTime)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.completedAt, Some(completedTime.truncatedTo(ChronoUnit.MICROS)))
        }
      yield ()
    }
  }

  test("updateCompletedAt returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.runningExecution()

      for
        _      <- repo.create(execution)
        result <- repo.updateCompletedAt(execution.executionId, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateResourceUsage updates CPU and memory") {
    withRepo { repo =>
      val execution = TestExecutions.runningExecution()

      for
        _         <- repo.create(execution)
        _         <- repo.updateResourceUsage(execution.executionId, 250000L, 1024000L)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.totalCpuMilliSeconds, 250000L)
          assertEquals(retrieved.get.totalMemoryMibSeconds, 1024000L)
        }
      yield ()
    }
  }

  test("updateResourceUsage returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.runningExecution()

      for
        _      <- repo.create(execution)
        result <- repo.updateResourceUsage(execution.executionId, 100000L, 512000L)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateConcurrencyQueuePosition updates position") {
    withRepo { repo =>
      val execution = TestExecutions.executionWithConcurrency(position = 5)

      for
        _         <- repo.create(execution)
        _         <- repo.updateConcurrencyQueuePosition(execution.executionId, 3)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.concurrencyQueuePosition, Some(3))
        }
      yield ()
    }
  }

  test("updateConcurrencyQueuePosition returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.executionWithConcurrency()

      for
        _      <- repo.create(execution)
        result <- repo.updateConcurrencyQueuePosition(execution.executionId, 2)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("softDelete marks execution as deleted") {
    withSeededDatabase { db =>
      val repo      = new ExecutionRepository(db)
      val execution = TestExecutions.queuedExecution()

      for
        _ <- repo.create(execution)
        _ <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        deletedAt <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT deleted_at FROM executions WHERE execution_id = ?"
            )
            stmt.setObject(1, execution.executionId)
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

  test("softDelete sets deleted_at timestamp") {
    withSeededDatabase { db =>
      val repo         = new ExecutionRepository(db)
      val execution    = TestExecutions.queuedExecution()
      val beforeDelete = Instant.now()

      for
        _ <- repo.create(execution)
        _ <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        _ <- IO.sleep(scala.concurrent.duration.Duration(10, "ms"))
        afterDelete = Instant.now()
        deletedAt <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT deleted_at FROM executions WHERE execution_id = ?"
            )
            stmt.setObject(1, execution.executionId)
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

  test("softDelete returns true when successful") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _      <- repo.create(execution)
        result <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("softDelete returns false when execution not found") {
    withRepo { repo =>
      val nonExistentId = UUID.randomUUID()
      repo.softDelete(nonExistentId, TestExecutions.testUser).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("softDelete makes execution invisible to findById") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _      <- repo.create(execution)
        _      <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        result <- repo.findById(execution.executionId)
        _      <- IO(assert(result.isEmpty, "Soft-deleted execution should not be found by findById"))
      yield ()
    }
  }

  test("softDelete cannot be performed multiple times on same execution") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        result1 <- repo.create(execution) >> repo.softDelete(execution.executionId, TestExecutions.testUser)
        result2 <- repo.softDelete(execution.executionId, TestExecutions.testUser)
        _ <- IO {
          assertEquals(result1, true, "First softDelete should succeed")
          assertEquals(result2, false, "Second softDelete should not succeed")
        }
      yield ()
    }
  }

  test("findTimedOut returns executions that have timed out") {
    withRepo { repo =>
      val now           = Instant.now()
      val timedOutExec  = TestExecutions.runningExecution().copy(timeoutAt = Some(now.minusSeconds(10)))
      val notTimedOut   = TestExecutions.runningExecution().copy(timeoutAt = Some(now.plusSeconds(3600)))
      val queuedTimeout = TestExecutions.queuedExecution().copy(timeoutAt = Some(now.minusSeconds(5)))

      for
        _       <- repo.create(timedOutExec)
        _       <- repo.create(notTimedOut)
        _       <- repo.create(queuedTimeout)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(
            results.exists(_.executionId == timedOutExec.executionId),
            "Should include timed out running execution"
          )
          assert(
            results.exists(_.executionId == queuedTimeout.executionId),
            "Should include timed out queued execution"
          )
          assert(
            !results.exists(_.executionId == notTimedOut.executionId),
            "Should not include executions with future timeout"
          )
        }
      yield ()
    }
  }

  test("findTimedOut only returns running or pending executions") {
    withRepo { repo =>
      val now       = Instant.now()
      val timedOut1 = TestExecutions.runningExecution().copy(timeoutAt = Some(now.minusSeconds(10)))
      val timedOut2 = TestExecutions.queuedExecution().copy(timeoutAt = Some(now.minusSeconds(10)))
      val completed = TestExecutions.completedExecution().copy(timeoutAt = Some(now.minusSeconds(10)))

      for
        _       <- repo.create(timedOut1)
        _       <- repo.create(timedOut2)
        _       <- repo.create(completed)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(results.exists(_.executionId == timedOut1.executionId), "Should include running timed out")
          assert(results.exists(_.executionId == timedOut2.executionId), "Should include pending timed out")
          assert(!results.exists(_.executionId == completed.executionId), "Should not include completed")
        }
      yield ()
    }
  }

  test("findTimedOut respects limit parameter") {
    withRepo { repo =>
      val now = Instant.now()
      val executions =
        (1 to 10).map(_ => TestExecutions.runningExecution().copy(timeoutAt = Some(now.minusSeconds(10))))

      for
        _       <- executions.toVector.traverse(repo.create(_))
        results <- repo.findTimedOut(now, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 executions, got ${results.length}"))
      yield ()
    }
  }

  test("findTimedOut excludes soft-deleted executions") {
    withRepo { repo =>
      val now       = Instant.now()
      val timedOut1 = TestExecutions.runningExecution().copy(timeoutAt = Some(now.minusSeconds(10)))
      val timedOut2 = TestExecutions.runningExecution().copy(timeoutAt = Some(now.minusSeconds(10)))

      for
        _       <- repo.create(timedOut1)
        _       <- repo.create(timedOut2)
        _       <- repo.softDelete(timedOut1.executionId, TestExecutions.testUser)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(!results.exists(_.executionId == timedOut1.executionId), "Should not include soft-deleted")
          assert(results.exists(_.executionId == timedOut2.executionId), "Should include active timed out")
        }
      yield ()
    }
  }

  test("transaction rollback on error") {
    withSeededDatabase { db =>
      val repo      = new ExecutionRepository(db)
      val execution = TestExecutions.queuedExecution()

      for
        _ <- repo.create(execution)
        // Try to create again - should fail
        result <- repo.create(execution).attempt
        _      <- IO(assert(result.isLeft))
        // Verify only one row exists (transaction rolled back properly)
        count <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT COUNT(*) FROM executions WHERE execution_id = ?"
            )
            stmt.setObject(1, execution.executionId)
            val rs = stmt.executeQuery()
            val c =
              if rs.next() then rs.getInt(1)
              else 0
            rs.close()
            stmt.close()
            c
          finally conn.close()
        }
        _ <- IO(assertEquals(count, 1))
      yield ()
    }
  }

  test("create multiple different executions") {
    withRepo { repo =>
      val exec1 = TestExecutions.queuedExecution()
      val exec2 = TestExecutions.runningExecution()
      val exec3 = TestExecutions.completedExecution()

      for
        id1 <- repo.create(exec1)
        id2 <- repo.create(exec2)
        id3 <- repo.create(exec3)
        _ <- IO {
          assertEquals(id1, exec1.executionId)
          assertEquals(id2, exec2.executionId)
          assertEquals(id3, exec3.executionId)
        }
      yield ()
    }
  }

  test("create then findById returns same execution") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        executionId <- repo.create(execution)
        retrieved   <- repo.findById(executionId)
        _ <- IO {
          assert(retrieved.isDefined, "Execution should be found after create")
          assertEquals(retrieved.get.executionId, execution.executionId)
          assertEquals(retrieved.get.status, execution.status)
        }
      yield ()
    }
  }

  test("concurrent creates with same ID handle duplicates correctly") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()

      for
        _     <- repo.create(execution)
        error <- repo.create(execution).intercept[StorageError.DuplicateKey]
        _ <- IO {
          assertEquals(error.entity, "Execution")
          assertEquals(error.id, execution.executionId.toString)
        }
      yield ()
    }
  }

  test("complete execution lifecycle") {
    withRepo { repo =>
      val execution = TestExecutions.queuedExecution()
      val startTime = Instant.now()
      val endTime   = startTime.plusSeconds(120)

      for
        // Create queued execution
        _         <- repo.create(execution)
        retrieved <- repo.findById(execution.executionId)
        _         <- IO(assertEquals(retrieved.get.status, ExecutionStatus.Pending))

        // Start execution
        _ <- repo.updateStatus(execution.executionId, ExecutionStatus.Running, None)
        _ <- repo.updateStartedAt(execution.executionId, startTime)

        // Update resource usage during execution
        _         <- repo.updateResourceUsage(execution.executionId, 50000L, 256000L)
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
          assertEquals(retrieved.get.startedAt, Some(startTime))
          assertEquals(retrieved.get.totalCpuMilliSeconds, 50000L)
        }

        // Complete execution
        _ <- repo.updateStatus(execution.executionId, ExecutionStatus.Completed, None)
        _ <- repo.updateCompletedAt(execution.executionId, endTime)
        _ <- repo.updateResourceUsage(execution.executionId, 120000L, 512000L)

        // Verify final state
        retrieved <- repo.findById(execution.executionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Completed)
          assertEquals(retrieved.get.startedAt, Some(startTime.truncatedTo(ChronoUnit.MICROS)))
          assertEquals(retrieved.get.completedAt, Some(endTime.truncatedTo(ChronoUnit.MICROS)))
          assertEquals(retrieved.get.totalCpuMilliSeconds, 120000L)
          assertEquals(retrieved.get.totalMemoryMibSeconds, 512000L)
        }
      yield ()
    }
  }
