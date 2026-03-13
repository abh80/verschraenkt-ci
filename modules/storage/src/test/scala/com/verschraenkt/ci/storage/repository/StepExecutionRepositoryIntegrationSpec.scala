package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestDatabaseFixture,
  TestExecutions,
  TestJobExecutions,
  TestStepExecutions,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit

class StepExecutionRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(74)
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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for step execution integration tests')
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

  /** Seeds pipeline, execution, workflow execution, and job execution; returns jobExecutionId */
  private def seedParentRecords(db: DatabaseModule): IO[Long] =
    val executionRepo = new ExecutionRepository(db)
    val wfRepo        = new WorkflowExecutionRepository(db)
    val jobRepo       = new JobExecutionRepository(db)
    val execution     = TestExecutions.queuedExecution()
    val wfExec        = TestWorkflowExecutions.queuedWorkflowExecution(executionId = execution.executionId)
    val jobExec = TestJobExecutions.queuedJobExecution(
      workflowExecutionId = wfExec.workflowExecutionId,
      executionId = execution.executionId
    )

    for
      _ <- executionRepo.create(execution)
      _ <- wfRepo.create(wfExec)
      _ <- jobRepo.create(jobExec)
    yield jobExec.jobExecutionId

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (StepExecutionRepository, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedParentRecords(db).flatMap { jobExecutionId =>
        f(new StepExecutionRepository(db), jobExecutionId)
      }
    }

  test("create inserts step execution into database") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId)

      for
        _      <- repo.create(step)
        result <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.stepExecutionId, step.stepExecutionId)
        }
      yield ()
    }
  }

  test("findById returns None when step execution does not exist") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findById(nonExistentId).map(p => assert(p.isEmpty))
    }
  }

  test("findById returns step execution when it exists") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.completedRunStep(jobExecutionId = jobExecutionId)

      for
        _      <- repo.create(step)
        result <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.stepExecutionId, step.stepExecutionId)
          assertEquals(result.get.status, ExecutionStatus.Completed)
          assertEquals(result.get.commandText, Some("echo 'Hello World'"))
        }
      yield ()
    }
  }

  test("findByJobExecutionId returns all steps ordered by stepIndex") {
    withRepo { (repo, jobExecutionId) =>
      val step0 = TestStepExecutions.checkoutStep(jobExecutionId = jobExecutionId, stepIndex = 0)
      val step1 = TestStepExecutions.completedRunStep(jobExecutionId = jobExecutionId, stepIndex = 1)
      val step2 = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId, stepIndex = 2)

      for
        _       <- repo.create(step2) // Insert out of order
        _       <- repo.create(step0)
        _       <- repo.create(step1)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.length >= 3)
          // Verify ordering by stepIndex
          val relevantResults = results.filter(s =>
            s.stepExecutionId == step0.stepExecutionId ||
              s.stepExecutionId == step1.stepExecutionId ||
              s.stepExecutionId == step2.stepExecutionId
          )
          assertEquals(relevantResults(0).stepIndex, 0)
          assertEquals(relevantResults(1).stepIndex, 1)
          assertEquals(relevantResults(2).stepIndex, 2)
        }
      yield ()
    }
  }

  test("findByJobExecutionId returns empty for non-existent job execution") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByJobExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  test("updateStatus changes step execution status") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId)

      for
        _         <- repo.create(step)
        _         <- repo.updateStatus(step.stepExecutionId, ExecutionStatus.Running, None)
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
        }
      yield ()
    }
  }

  test("updateStatus with error message") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.runStep(jobExecutionId = jobExecutionId)

      for
        _ <- repo.create(step)
        _ <- repo.updateStatus(
          step.stepExecutionId,
          ExecutionStatus.Failed,
          Some("Process exited with code 1")
        )
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.status, ExecutionStatus.Failed)
          assertEquals(retrieved.get.errorMessage, Some("Process exited with code 1"))
        }
      yield ()
    }
  }

  test("updateStatus returns true when successful") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId)

      for
        _      <- repo.create(step)
        result <- repo.updateStatus(step.stepExecutionId, ExecutionStatus.Running, None)
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
    withRepo { (repo, jobExecutionId) =>
      val step      = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId)
      val startTime = Instant.now()

      for
        _         <- repo.create(step)
        _         <- repo.updateStartedAt(step.stepExecutionId, startTime)
        retrieved <- repo.findById(step.stepExecutionId)
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
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId)

      for
        _      <- repo.create(step)
        result <- repo.updateStartedAt(step.stepExecutionId, Instant.now())
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

  test("updateCompleted sets completed time and exit code") {
    withRepo { (repo, jobExecutionId) =>
      val step          = TestStepExecutions.runStep(jobExecutionId = jobExecutionId)
      val completedTime = Instant.now()

      for
        _         <- repo.create(step)
        _         <- repo.updateCompleted(step.stepExecutionId, completedTime, Some(0))
        retrieved <- repo.findById(step.stepExecutionId)
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

  test("updateCompleted with failure exit code") {
    withRepo { (repo, jobExecutionId) =>
      val step          = TestStepExecutions.runStep(jobExecutionId = jobExecutionId, stepIndex = 2)
      val completedTime = Instant.now()

      for
        _         <- repo.create(step)
        _         <- repo.updateCompleted(step.stepExecutionId, completedTime, Some(1))
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.exitCode, Some(1))
        }
      yield ()
    }
  }

  test("updateCompleted returns true when successful") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.runStep(jobExecutionId = jobExecutionId, stepIndex = 3)

      for
        _      <- repo.create(step)
        result <- repo.updateCompleted(step.stepExecutionId, Instant.now(), Some(0))
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateCompleted returns false when not found") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateCompleted(nonExistentId, Instant.now(), Some(0)).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("updateOutputLocations sets stdout and stderr locations") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId, stepIndex = 5)

      for
        _ <- repo.create(step)
        _ <- repo.updateOutputLocations(
          step.stepExecutionId,
          Some("s3://logs/stdout.log"),
          Some("s3://logs/stderr.log")
        )
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.stdoutLocation, Some("s3://logs/stdout.log"))
          assertEquals(retrieved.get.stderrLocation, Some("s3://logs/stderr.log"))
        }
      yield ()
    }
  }

  test("updateOutputLocations with only stdout") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId, stepIndex = 6)

      for
        _         <- repo.create(step)
        _         <- repo.updateOutputLocations(step.stepExecutionId, Some("s3://logs/stdout.log"), None)
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.stdoutLocation, Some("s3://logs/stdout.log"))
          assertEquals(retrieved.get.stderrLocation, None)
        }
      yield ()
    }
  }

  test("updateOutputLocations returns true when successful") {
    withRepo { (repo, jobExecutionId) =>
      val step = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId, stepIndex = 7)

      for
        _      <- repo.create(step)
        result <- repo.updateOutputLocations(step.stepExecutionId, Some("s3://logs/stdout.log"), None)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateOutputLocations returns false when not found") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.updateOutputLocations(nonExistentId, Some("s3://logs/stdout.log"), None).map { result =>
        assertEquals(result, false)
      }
    }
  }

  test("complete step execution lifecycle") {
    withRepo { (repo, jobExecutionId) =>
      val step      = TestStepExecutions.pendingStep(jobExecutionId = jobExecutionId, stepIndex = 8)
      val startTime = Instant.now()
      val endTime   = startTime.plusSeconds(30)

      for
        // Create pending step
        _         <- repo.create(step)
        retrieved <- repo.findById(step.stepExecutionId)
        _         <- IO(assertEquals(retrieved.get.status, ExecutionStatus.Pending))

        // Start step
        _ <- repo.updateStatus(step.stepExecutionId, ExecutionStatus.Running, None)
        _ <- repo.updateStartedAt(step.stepExecutionId, startTime)

        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Running)
          assertInstantWithinTolerance(
            retrieved.get.startedAt,
            Some(startTime.truncatedTo(ChronoUnit.MICROS))
          )
        }

        // Set output locations
        _ <- repo.updateOutputLocations(
          step.stepExecutionId,
          Some("s3://logs/stdout.log"),
          Some("s3://logs/stderr.log")
        )

        // Complete step
        _ <- repo.updateStatus(step.stepExecutionId, ExecutionStatus.Completed, None)
        _ <- repo.updateCompleted(step.stepExecutionId, endTime, Some(0))

        // Verify final state
        retrieved <- repo.findById(step.stepExecutionId)
        _ <- IO {
          assertEquals(retrieved.get.status, ExecutionStatus.Completed)
          assertEquals(retrieved.get.exitCode, Some(0))
          assertInstantWithinTolerance(
            retrieved.get.completedAt,
            Some(endTime.truncatedTo(ChronoUnit.MICROS))
          )
          assertEquals(retrieved.get.stdoutLocation, Some("s3://logs/stdout.log"))
          assertEquals(retrieved.get.stderrLocation, Some("s3://logs/stderr.log"))
        }
      yield ()
    }
  }
