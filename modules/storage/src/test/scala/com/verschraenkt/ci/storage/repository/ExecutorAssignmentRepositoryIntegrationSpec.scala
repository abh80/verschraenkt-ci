package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestDatabaseFixture,
  TestExecutions,
  TestExecutorAssignments,
  TestExecutors,
  TestJobExecutions,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExecutorAssignmentRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(78)
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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for executor assignment integration tests')
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

  /** Seeds pipeline, execution, workflow execution, and job execution; returns (executionId, jobExecutionId)
    */
  private def seedParentRecords(db: DatabaseModule): IO[(Long, Long)] =
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
    yield (execution.executionId, jobExec.jobExecutionId)

  private def seedExecutor(db: DatabaseModule): IO[UUID] =
    val executorRepo = new ExecutorRepository(db)
    val executor     = TestExecutors.onlineExecutor()
    executorRepo.save(executor)

  /** Seeds another job execution under the same parent chain */
  private def seedAnotherJobExecution(db: DatabaseModule, executionId: Long): IO[Long] =
    val wfRepo  = new WorkflowExecutionRepository(db)
    val jobRepo = new JobExecutionRepository(db)
    val wfExec  = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
    val jobExec = TestJobExecutions.queuedJobExecution(
      workflowExecutionId = wfExec.workflowExecutionId,
      executionId = executionId
    )

    for
      _ <- wfRepo.create(wfExec)
      _ <- jobRepo.create(jobExec)
    yield jobExec.jobExecutionId

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (ExecutorAssignmentRepository, Long, UUID) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      for
        (_, jobExecutionId) <- seedParentRecords(db)
        executorId          <- seedExecutor(db)
        result              <- f(new ExecutorAssignmentRepository(db), jobExecutionId, executorId)
      yield result
    }

  // --- create ---

  test("create inserts executor assignment and returns generated ID") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        id     <- repo.create(assignment)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.jobExecutionId, jobExecutionId)
          assertEquals(result.get.executorId, executorId)
        }
      yield ()
    }
  }

  // --- findById ---

  test("findById returns assignment when it exists") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        id     <- repo.create(assignment)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.assignmentId, Some(id))
          assertEquals(result.get.jobExecutionId, jobExecutionId)
          assertEquals(result.get.executorId, executorId)
        }
      yield ()
    }
  }

  test("findById returns None when assignment does not exist") {
    withRepo { (repo, _, _) =>
      repo.findById(UUID.randomUUID()).map(r => assert(r.isEmpty))
    }
  }

  // --- findByJobExecutionId ---

  test("findByJobExecutionId returns assignment for the job") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        _      <- repo.create(assignment)
        result <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.jobExecutionId, jobExecutionId)
        }
      yield ()
    }
  }

  test("findByJobExecutionId returns None when no assignment exists") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByJobExecutionId(nonExistentId).map(r => assert(r.isEmpty))
    }
  }

  // --- findByExecutorId ---

  test("findByExecutorId returns all assignments for an executor") {
    withSeededDatabase { db =>
      for
        (executionId, jobExecId1) <- seedParentRecords(db)
        executorId                <- seedExecutor(db)
        jobExecId2                <- seedAnotherJobExecution(db, executionId)
        repo = new ExecutorAssignmentRepository(db)
        assignment1 = TestExecutorAssignments.pendingAssignment(
          jobExecutionId = jobExecId1,
          executorId = executorId
        )
        assignment2 = TestExecutorAssignments.pendingAssignment(
          jobExecutionId = jobExecId2,
          executorId = executorId
        )
        _       <- repo.create(assignment1)
        _       <- repo.create(assignment2)
        results <- repo.findByExecutorId(executorId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 assignments, got ${results.length}")
          assert(results.forall(_.executorId == executorId))
        }
      yield ()
    }
  }

  test("findByExecutorId returns empty for executor with no assignments") {
    withRepo { (repo, _, _) =>
      val nonExistentId = UUID.randomUUID()
      repo.findByExecutorId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- updateStartedAt ---

  test("updateStartedAt sets started time") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )
      val startTime = Instant.now()

      for
        id        <- repo.create(assignment)
        _         <- repo.updateStartedAt(id, startTime)
        retrieved <- repo.findById(id)
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
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        id     <- repo.create(assignment)
        result <- repo.updateStartedAt(id, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateStartedAt returns false when assignment not found") {
    withRepo { (repo, _, _) =>
      repo.updateStartedAt(UUID.randomUUID(), Instant.now()).map(result => assertEquals(result, false))
    }
  }

  // --- updateCompletedAt ---

  test("updateCompletedAt sets completed time") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.activeAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )
      val completedTime = Instant.now()

      for
        id        <- repo.create(assignment)
        _         <- repo.updateCompletedAt(id, completedTime)
        retrieved <- repo.findById(id)
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
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.activeAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        id     <- repo.create(assignment)
        result <- repo.updateCompletedAt(id, Instant.now())
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("updateCompletedAt returns false when assignment not found") {
    withRepo { (repo, _, _) =>
      repo.updateCompletedAt(UUID.randomUUID(), Instant.now()).map(result => assertEquals(result, false))
    }
  }

  // --- lifecycle test ---

  test("complete assignment lifecycle") {
    withRepo { (repo, jobExecutionId, executorId) =>
      val assignment = TestExecutorAssignments.pendingAssignment(
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )
      val startTime    = Instant.now()
      val completeTime = startTime.plusSeconds(120)

      for
        // Create assignment
        id        <- repo.create(assignment)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.startedAt, None)
          assertEquals(retrieved.get.completedAt, None)
        }

        // Start work
        _         <- repo.updateStartedAt(id, startTime)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.get.startedAt.isDefined)
          assertEquals(retrieved.get.completedAt, None)
        }

        // Complete work
        _         <- repo.updateCompletedAt(id, completeTime)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.get.startedAt.isDefined)
          assert(retrieved.get.completedAt.isDefined)
        }

        // Verify findByJobExecutionId
        byJob <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(byJob.isDefined)
          assertEquals(byJob.get.assignmentId, Some(id))
        }
      yield ()
    }
  }
