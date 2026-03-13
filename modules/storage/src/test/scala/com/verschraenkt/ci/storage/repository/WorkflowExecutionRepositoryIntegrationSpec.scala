package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestDatabaseFixture,
  TestExecutions,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit

class WorkflowExecutionRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(72)
  private def getNextSnowflake  = snowflakeProvider.nextId()

  private val seededPipelineIds: Vector[String] =
    Vector("test-pipeline") ++ (1 to 500).map(i => s"pipeline-$i")

  private val dbTimestampToleranceNanos = 1000L

  private def assertInstantWithinTolerance(
      actual: Option[Instant],
      expected: Option[Instant],
      toleranceNanos: Long = dbTimestampToleranceNanos
  ): Unit =
    (actual, expected) match
      case (Some(actualValue), Some(expectedValue)) =>
        val diff = Math.abs(java.time.Duration.between(expectedValue, actualValue).toNanos)
        assert(
          diff <= toleranceNanos,
          s"Expected ${expected.toString} within ${toleranceNanos}ns, but got ${actual.toString} (diff=${diff}ns)"
        )
      case _ =>
        assertEquals(actual, expected)

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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for workflow execution integration tests')
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

  private def seedExecution(db: DatabaseModule): IO[Long] =
    val executionRepo = new ExecutionRepository(db)
    val execution     = TestExecutions.queuedExecution()
    executionRepo.create(execution).map(_ => execution.executionId)

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (WorkflowExecutionRepository, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedExecution(db).flatMap { executionId =>
        f(new WorkflowExecutionRepository(db), executionId)
      }
    }

  test("create inserts workflow execution into database") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)

      for
        _      <- repo.create(wfExec)
        result <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.workflowExecutionId, wfExec.workflowExecutionId)
        }
      yield ()
    }
  }

  test("findById returns None when workflow execution does not exist") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findById(nonExistentId).map(p => assert(p.isEmpty))
    }
  }

  test("findById returns workflow execution when it exists") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)

      for
        _      <- repo.create(wfExec)
        result <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.workflowExecutionId, wfExec.workflowExecutionId)
          assertEquals(result.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("findByExecutionId returns all workflow executions for an execution") {
    withRepo { (repo, executionId) =>
      val wf1 = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
      val wf2 = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)
      val wf3 = TestWorkflowExecutions.completedWorkflowExecution(executionId = executionId)

      for
        _       <- repo.create(wf1)
        _       <- repo.create(wf2)
        _       <- repo.create(wf3)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.length >= 3)
          assert(results.exists(_.workflowExecutionId == wf1.workflowExecutionId))
          assert(results.exists(_.workflowExecutionId == wf2.workflowExecutionId))
          assert(results.exists(_.workflowExecutionId == wf3.workflowExecutionId))
        }
      yield ()
    }
  }

  test("findByExecutionId returns empty for non-existent execution") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  test("findByExecutionIdAndStatus filters by status") {
    withRepo { (repo, executionId) =>
      val wf1 = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
      val wf2 = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)
      val wf3 = TestWorkflowExecutions.completedWorkflowExecution(executionId = executionId)

      for
        _       <- repo.create(wf1)
        _       <- repo.create(wf2)
        _       <- repo.create(wf3)
        results <- repo.findByExecutionIdAndStatus(executionId, ExecutionStatus.Running)
        _ <- IO {
          assert(results.exists(_.workflowExecutionId == wf2.workflowExecutionId))
          assert(!results.exists(_.workflowExecutionId == wf1.workflowExecutionId))
          assert(!results.exists(_.workflowExecutionId == wf3.workflowExecutionId))
        }
      yield ()
    }
  }

  test("updateStatus changes workflow execution status") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)

      for
        _         <- repo.create(wfExec)
        _         <- repo.updateStatus(wfExec.workflowExecutionId, ExecutionStatus.Running, None)
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("updateStatus with error message") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)

      for
        _         <- repo.create(wfExec)
        _         <- repo.updateStatus(wfExec.workflowExecutionId, ExecutionStatus.Failed, Some("Test error"))
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Failed)
          assertEquals(retrieved.get.errorMessage, Some("Test error"))
        }
      yield ()
    }
  }

  test("updateStatus returns true when successful") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)

      for
        _      <- repo.create(wfExec)
        result <- repo.updateStatus(wfExec.workflowExecutionId, ExecutionStatus.Running, None)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStatus returns false when not found") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateStatus(nonExistentId, ExecutionStatus.Running, None).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateStartedAt sets started time") {
    withRepo { (repo, executionId) =>
      val wfExec    = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
      val startTime = Instant.now()

      for
        _         <- repo.create(wfExec)
        _         <- repo.updateStartedAt(wfExec.workflowExecutionId, startTime)
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertInstantWithinTolerance(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          )
        }
      yield ()
    }
  }

  test("updateStartedAt returns true when successful") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)

      for
        _      <- repo.create(wfExec)
        result <- repo.updateStartedAt(wfExec.workflowExecutionId, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStartedAt returns false when not found") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateStartedAt(nonExistentId, Instant.now()).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateCompletedAt sets completed time") {
    withRepo { (repo, executionId) =>
      val wfExec        = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)
      val completedTime = Instant.now()

      for
        _         <- repo.create(wfExec)
        _         <- repo.updateCompletedAt(wfExec.workflowExecutionId, completedTime)
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertInstantWithinTolerance(
            retrieved.get.completedAt,
            Some(completedTime.truncatedTo(ChronoUnit.MICROS))
          )
        }
      yield ()
    }
  }

  test("updateCompletedAt returns true when successful") {
    withRepo { (repo, executionId) =>
      val wfExec = TestWorkflowExecutions.runningWorkflowExecution(executionId = executionId)

      for
        _      <- repo.create(wfExec)
        result <- repo.updateCompletedAt(wfExec.workflowExecutionId, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("findTimedOut returns workflow executions that have timed out") {
    withRepo { (repo, executionId) =>
      val now = Instant.now()
      val timedOut = TestWorkflowExecutions
        .runningWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(10)))
      val notTimedOut = TestWorkflowExecutions
        .runningWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.plusSeconds(3600)))
      val queuedTimeout = TestWorkflowExecutions
        .queuedWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(5)))

      for
        _       <- repo.create(timedOut)
        _       <- repo.create(notTimedOut)
        _       <- repo.create(queuedTimeout)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(
            results.exists(_.workflowExecutionId == timedOut.workflowExecutionId),
            "Should include timed out running workflow"
          )
          assert(
            results.exists(_.workflowExecutionId == queuedTimeout.workflowExecutionId),
            "Should include timed out queued workflow"
          )
          assert(
            !results.exists(_.workflowExecutionId == notTimedOut.workflowExecutionId),
            "Should not include workflows with future timeout"
          )
        }
      yield ()
    }
  }

  test("findTimedOut only returns running or pending workflow executions") {
    withRepo { (repo, executionId) =>
      val now = Instant.now()
      val timedOut1 = TestWorkflowExecutions
        .runningWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(10)))
      val timedOut2 = TestWorkflowExecutions
        .queuedWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(10)))
      val completed = TestWorkflowExecutions
        .completedWorkflowExecution(executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(10)))

      for
        _       <- repo.create(timedOut1)
        _       <- repo.create(timedOut2)
        _       <- repo.create(completed)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(
            results.exists(_.workflowExecutionId == timedOut1.workflowExecutionId),
            "Should include running timed out"
          )
          assert(
            results.exists(_.workflowExecutionId == timedOut2.workflowExecutionId),
            "Should include pending timed out"
          )
          assert(
            !results.exists(_.workflowExecutionId == completed.workflowExecutionId),
            "Should not include completed"
          )
        }
      yield ()
    }
  }

  test("findTimedOut respects limit parameter") {
    withRepo { (repo, executionId) =>
      val now = Instant.now()
      val executions = (1 to 10).map(_ =>
        TestWorkflowExecutions
          .runningWorkflowExecution(executionId = executionId)
          .copy(timeoutAt = Some(now.minusSeconds(10)))
      )

      for
        _       <- executions.toVector.traverse(repo.create(_))
        results <- repo.findTimedOut(now, 5)
        _ <- IO(
          assert(results.length <= 5, s"Should return at most 5 workflow executions, got ${results.length}")
        )
      yield ()
    }
  }

  test("complete workflow execution lifecycle") {
    withRepo { (repo, executionId) =>
      val wfExec    = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
      val startTime = Instant.now()
      val endTime   = startTime.plusSeconds(120)

      for
        // Create queued workflow execution
        _         <- repo.create(wfExec)
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _         <- IO(assertEquals(retrieved.get.status, ExecutionStatus.Pending))

        // Start execution
        _ <- repo.updateStatus(wfExec.workflowExecutionId, ExecutionStatus.Running, None)
        _ <- repo.updateStartedAt(wfExec.workflowExecutionId, startTime)

        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
          assertInstantWithinTolerance(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          )
        }

        // Complete execution
        _ <- repo.updateStatus(wfExec.workflowExecutionId, ExecutionStatus.Completed, None)
        _ <- repo.updateCompletedAt(wfExec.workflowExecutionId, endTime)

        // Verify final state
        retrieved <- repo.findById(wfExec.workflowExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Completed)
          assertInstantWithinTolerance(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          )
          assertInstantWithinTolerance(
            retrieved.get.completedAt,
            Some(endTime.truncatedTo(ChronoUnit.MICROS))
          )
        }
      yield ()
    }
  }
