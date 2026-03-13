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
  TestExecutors,
  TestJobExecutions,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class JobExecutionRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(73)
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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for job execution integration tests')
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

  /** Seeds pipeline, execution, and workflow execution; returns (executionId, workflowExecutionId) */
  private def seedParentRecords(db: DatabaseModule): IO[(Long, Long)] =
    val executionRepo = new ExecutionRepository(db)
    val wfRepo        = new WorkflowExecutionRepository(db)
    val execution     = TestExecutions.queuedExecution()
    val wfExec        = TestWorkflowExecutions.queuedWorkflowExecution(executionId = execution.executionId)

    for
      _ <- executionRepo.create(execution)
      _ <- wfRepo.create(wfExec)
    yield (execution.executionId, wfExec.workflowExecutionId)

  private def seedExecutor(db: DatabaseModule, executorId: UUID): IO[UUID] =
    val executorRepo = new ExecutorRepository(db)
    val executor     = TestExecutors.onlineExecutor().copy(executorId = Some(executorId))

    executorRepo.save(executor)

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (JobExecutionRepository, Long, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedParentRecords(db).flatMap { case (executionId, workflowExecutionId) =>
        f(new JobExecutionRepository(db), executionId, workflowExecutionId)
      }
    }

  private def withRepoAndExecutor[A](f: (JobExecutionRepository, Long, Long, UUID) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      for
        (executionId, workflowExecutionId) <- seedParentRecords(db)
        executorId                         <- seedExecutor(db, UUID.randomUUID())
        result <- f(new JobExecutionRepository(db), executionId, workflowExecutionId, executorId)
      yield result
    }

  test("create inserts job execution into database") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _      <- repo.create(jobExec)
        result <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.jobExecutionId, jobExec.jobExecutionId)
        }
      yield ()
    }
  }

  test("findById returns None when job execution does not exist") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findById(nonExistentId).map(p => assert(p.isEmpty))
    }
  }

  test("findById returns job execution when it exists") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec = TestJobExecutions
        .runningJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(executorId = None, assignedAt = None)

      for
        _      <- repo.create(jobExec)
        result <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.jobExecutionId, jobExec.jobExecutionId)
          assertEquals(result.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("findByWorkflowExecutionId returns all job executions for a workflow execution") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val job1 =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val job2 =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val job3 =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _       <- repo.create(job1)
        _       <- repo.create(job2)
        _       <- repo.create(job3)
        results <- repo.findByWorkflowExecutionId(wfExecutionId)
        _ <- IO {
          assert(results.length >= 3)
          assert(results.exists(_.jobExecutionId == job1.jobExecutionId))
          assert(results.exists(_.jobExecutionId == job2.jobExecutionId))
          assert(results.exists(_.jobExecutionId == job3.jobExecutionId))
        }
      yield ()
    }
  }

  test("findByExecutionId returns all job executions for a pipeline execution") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val job1 =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val job2 =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _       <- repo.create(job1)
        _       <- repo.create(job2)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.length >= 2)
          assert(results.exists(_.jobExecutionId == job1.jobExecutionId))
          assert(results.exists(_.jobExecutionId == job2.jobExecutionId))
        }
      yield ()
    }
  }

  test("findByStatus returns job executions with matching status") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val pending =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val completed = TestJobExecutions
        .completedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(executorId = None, assignedAt = None)

      for
        _       <- repo.create(pending)
        _       <- repo.create(completed)
        results <- repo.findByStatus(ExecutionStatus.Pending, 100)
        _ <- IO {
          assert(results.exists(_.jobExecutionId == pending.jobExecutionId))
          assert(!results.exists(_.jobExecutionId == completed.jobExecutionId))
        }
      yield ()
    }
  }

  test("findByStatus respects limit parameter") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobs = (1 to 10).map(_ =>
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      )

      for
        _       <- jobs.toVector.traverse(repo.create(_))
        results <- repo.findByStatus(ExecutionStatus.Pending, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 job executions, got ${results.length}"))
      yield ()
    }
  }

  test("updateStatus changes job execution status") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _         <- repo.create(jobExec)
        _         <- repo.updateStatus(jobExec.jobExecutionId, ExecutionStatus.Running, None)
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("updateStatus with error message") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _         <- repo.create(jobExec)
        _         <- repo.updateStatus(jobExec.jobExecutionId, ExecutionStatus.Failed, Some("Build failed"))
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Failed)
          assertEquals(retrieved.get.errorMessage, Some("Build failed"))
        }
      yield ()
    }
  }

  test("updateStatus returns true when successful") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _      <- repo.create(jobExec)
        result <- repo.updateStatus(jobExec.jobExecutionId, ExecutionStatus.Running, None)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStatus returns false when not found") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateStatus(nonExistentId, ExecutionStatus.Running, None).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("assignExecutor sets executor and assigned time") {
    withRepoAndExecutor { (repo, executionId, wfExecutionId, executorUuid) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val assignedAt = Instant.now()

      for
        _         <- repo.create(jobExec)
        _         <- repo.assignExecutor(jobExec.jobExecutionId, executorUuid, assignedAt)
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.executorId, Some(executorUuid))
          assertInstantWithinTolerance(
            retrieved.get.assignedAt,
            Some(assignedAt.truncatedTo(ChronoUnit.MICROS))
          )
        }
      yield ()
    }
  }

  test("assignExecutor returns true when successful") {
    withRepoAndExecutor { (repo, executionId, wfExecutionId, executorUuid) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _      <- repo.create(jobExec)
        result <- repo.assignExecutor(jobExec.jobExecutionId, executorUuid, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("assignExecutor returns false when not found") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.assignExecutor(nonExistentId, UUID.randomUUID(), Instant.now()).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateStartedAt sets started time") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val startTime = Instant.now()

      for
        _         <- repo.create(jobExec)
        _         <- repo.updateStartedAt(jobExec.jobExecutionId, startTime)
        retrieved <- repo.findById(jobExec.jobExecutionId)
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

  test("updateStartedAt returns false when not found") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateStartedAt(nonExistentId, Instant.now()).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateCompleted sets completed time and exit code") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val completedTime = Instant.now()

      for
        _         <- repo.create(jobExec)
        _         <- repo.updateCompleted(jobExec.jobExecutionId, completedTime, Some(0))
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertInstantWithinTolerance(
            retrieved.get.completedAt,
            Some(completedTime.truncatedTo(ChronoUnit.MICROS))
          )
          assertEquals(retrieved.get.exitCode, Some(0))
        }
      yield ()
    }
  }

  test("updateCompleted returns true when successful") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _      <- repo.create(jobExec)
        result <- repo.updateCompleted(jobExec.jobExecutionId, Instant.now(), Some(1))
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateResourceUsage updates CPU and memory") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _         <- repo.create(jobExec)
        _         <- repo.updateResourceUsage(jobExec.jobExecutionId, 250000L, 1024000L)
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.actualCpuMilliSeconds, Some(250000L))
          assertEquals(retrieved.get.actualMemoryMibSeconds, Some(1024000L))
        }
      yield ()
    }
  }

  test("updateResourceUsage returns true when successful") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)

      for
        _      <- repo.create(jobExec)
        result <- repo.updateResourceUsage(jobExec.jobExecutionId, 100000L, 512000L)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("findTimedOut returns job executions that have timed out") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val now = Instant.now()
      val timedOut = TestJobExecutions
        .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(status = ExecutionStatus.Running, timeoutAt = Some(now.minusSeconds(10)))
      val notTimedOut = TestJobExecutions
        .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(status = ExecutionStatus.Running, timeoutAt = Some(now.plusSeconds(3600)))
      val queuedTimeout = TestJobExecutions
        .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(5)))

      for
        _       <- repo.create(timedOut)
        _       <- repo.create(notTimedOut)
        _       <- repo.create(queuedTimeout)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(
            results.exists(_.jobExecutionId == timedOut.jobExecutionId),
            "Should include timed out running job"
          )
          assert(
            results.exists(_.jobExecutionId == queuedTimeout.jobExecutionId),
            "Should include timed out queued job"
          )
          assert(
            !results.exists(_.jobExecutionId == notTimedOut.jobExecutionId),
            "Should not include jobs with future timeout"
          )
        }
      yield ()
    }
  }

  test("findTimedOut only returns running or pending job executions") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val now = Instant.now()
      val timedOut1 = TestJobExecutions
        .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(status = ExecutionStatus.Running, timeoutAt = Some(now.minusSeconds(10)))
      val timedOut2 = TestJobExecutions
        .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(timeoutAt = Some(now.minusSeconds(10)))
      val completed = TestJobExecutions
        .completedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
        .copy(executorId = None, assignedAt = None, timeoutAt = Some(now.minusSeconds(10)))

      for
        _       <- repo.create(timedOut1)
        _       <- repo.create(timedOut2)
        _       <- repo.create(completed)
        results <- repo.findTimedOut(now, 100)
        _ <- IO {
          assert(
            results.exists(_.jobExecutionId == timedOut1.jobExecutionId),
            "Should include running timed out"
          )
          assert(
            results.exists(_.jobExecutionId == timedOut2.jobExecutionId),
            "Should include pending timed out"
          )
          assert(
            !results.exists(_.jobExecutionId == completed.jobExecutionId),
            "Should not include completed"
          )
        }
      yield ()
    }
  }

  test("findTimedOut respects limit parameter") {
    withRepo { (repo, executionId, wfExecutionId) =>
      val now = Instant.now()
      val jobs = (1 to 10).map(_ =>
        TestJobExecutions
          .queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
          .copy(status = ExecutionStatus.Running, timeoutAt = Some(now.minusSeconds(10)))
      )

      for
        _       <- jobs.toVector.traverse(repo.create(_))
        results <- repo.findTimedOut(now, 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 job executions, got ${results.length}"))
      yield ()
    }
  }

  test("complete job execution lifecycle") {
    withRepoAndExecutor { (repo, executionId, wfExecutionId, executorUuid) =>
      val jobExec =
        TestJobExecutions.queuedJobExecution(workflowExecutionId = wfExecutionId, executionId = executionId)
      val assignTime = Instant.now()
      val startTime  = assignTime.plusSeconds(5)
      val endTime    = startTime.plusSeconds(120)

      for
        // Create queued job
        _         <- repo.create(jobExec)
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _         <- IO(assertEquals(retrieved.get.status, ExecutionStatus.Pending))

        // Assign executor
        _ <- repo.assignExecutor(jobExec.jobExecutionId, executorUuid, assignTime)

        // Start execution
        _ <- repo.updateStatus(jobExec.jobExecutionId, ExecutionStatus.Running, None)
        _ <- repo.updateStartedAt(jobExec.jobExecutionId, startTime)

        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
          assertEquals(retrieved.get.executorId, Some(executorUuid))
          assertInstantWithinTolerance(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          )
        }

        // Update resource usage
        _ <- repo.updateResourceUsage(jobExec.jobExecutionId, 50000L, 256000L)

        // Complete execution
        _ <- repo.updateStatus(jobExec.jobExecutionId, ExecutionStatus.Completed, None)
        _ <- repo.updateCompleted(jobExec.jobExecutionId, endTime, Some(0))
        _ <- repo.updateResourceUsage(jobExec.jobExecutionId, 120000L, 512000L)

        // Verify final state
        retrieved <- repo.findById(jobExec.jobExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Completed)
          assertEquals(retrieved.get.exitCode, Some(0))
          assertInstantWithinTolerance(
            retrieved.get.completedAt,
            Some(endTime.truncatedTo(ChronoUnit.MICROS))
          )
          assertEquals(retrieved.get.actualCpuMilliSeconds, Some(120000L))
          assertEquals(retrieved.get.actualMemoryMibSeconds, Some(512000L))
        }
      yield ()
    }
  }
