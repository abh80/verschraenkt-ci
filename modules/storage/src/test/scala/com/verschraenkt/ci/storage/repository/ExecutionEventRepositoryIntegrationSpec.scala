package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestDatabaseFixture,
  TestExecutionEvents,
  TestExecutions
}
import munit.CatsEffectSuite

import java.util.UUID

class ExecutionEventRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(75)
  private def getNextSnowflake  = snowflakeProvider.nextId()

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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for execution event integration tests')
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

  private def withRepo[A](f: (ExecutionEventRepository, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedExecution(db).flatMap { executionId =>
        f(new ExecutionEventRepository(db), executionId)
      }
    }

  // --- create ---

  test("create inserts execution event into database") {
    withRepo { (repo, executionId) =>
      val event = TestExecutionEvents.statusChangeEvent(executionId = executionId)

      for
        _       <- repo.create(event)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.nonEmpty, "Should find the created event")
          assertEquals(results.head.executionId, executionId)
          assertEquals(results.head.eventType, "status_change")
        }
      yield ()
    }
  }

  test("create multiple events for the same execution") {
    withRepo { (repo, executionId) =>
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId)
      val event3 = TestExecutionEvents.metricEvent(executionId = executionId)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.length >= 3, s"Should have at least 3 events, got ${results.length}")
        }
      yield ()
    }
  }

  // --- findByExecutionId ---

  test("findByExecutionId returns events for the given execution") {
    withRepo { (repo, executionId) =>
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.length >= 2)
          assert(results.forall(_.executionId == executionId))
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

  test("findByExecutionId respects limit parameter") {
    withRepo { (repo, executionId) =>
      val events = (1 to 10).map(_ => TestExecutionEvents.event(executionId = executionId))

      for
        _       <- events.toVector.traverse(repo.create(_))
        results <- repo.findByExecutionId(executionId, limit = 5)
        _       <- IO(assert(results.length <= 5, s"Should return at most 5 events, got ${results.length}"))
      yield ()
    }
  }

  // --- findByWorkflowExecutionId ---

  test("findByWorkflowExecutionId returns matching events") {
    withRepo { (repo, executionId) =>
      val wfId = getNextSnowflake.value
      val event1 =
        TestExecutionEvents.statusChangeEvent(executionId = executionId, workflowExecutionId = wfId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId, workflowExecutionId = wfId)
      val event3 =
        TestExecutionEvents.event(executionId = executionId, workflowExecutionId = getNextSnowflake.value)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        results <- repo.findByWorkflowExecutionId(wfId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 events for wf, got ${results.length}")
          assert(results.forall(_.workflowExecutionId == wfId))
        }
      yield ()
    }
  }

  test("findByWorkflowExecutionId returns empty for non-existent workflow execution") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByWorkflowExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- findByJobExecutionId ---

  test("findByJobExecutionId returns matching events") {
    withRepo { (repo, executionId) =>
      val jobId  = getNextSnowflake.value
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId, jobExecutionId = jobId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId, jobExecutionId = jobId)
      val event3 =
        TestExecutionEvents.event(executionId = executionId, jobExecutionId = getNextSnowflake.value)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        results <- repo.findByJobExecutionId(jobId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 events for job, got ${results.length}")
          assert(results.forall(_.jobExecutionId == jobId))
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

  // --- findByStepExecutionId ---

  test("findByStepExecutionId returns matching events") {
    withRepo { (repo, executionId) =>
      val stepId = getNextSnowflake.value
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId, stepExecutionId = stepId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId, stepExecutionId = stepId)
      val event3 =
        TestExecutionEvents.event(executionId = executionId, stepExecutionId = getNextSnowflake.value)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        results <- repo.findByStepExecutionId(stepId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 events for step, got ${results.length}")
          assert(results.forall(_.stepExecutionId == stepId))
        }
      yield ()
    }
  }

  test("findByStepExecutionId returns empty for non-existent step execution") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByStepExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- findByCorrelationId ---

  test("findByCorrelationId returns matching events") {
    withRepo { (repo, executionId) =>
      val corrId = UUID.randomUUID()
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId, correlationId = corrId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId, correlationId = corrId)
      val event3 = TestExecutionEvents.event(executionId = executionId, correlationId = UUID.randomUUID())

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        results <- repo.findByCorrelationId(corrId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 events for correlation, got ${results.length}")
          assert(results.forall(_.correlationId == corrId))
        }
      yield ()
    }
  }

  test("findByCorrelationId returns empty for non-existent correlation ID") {
    withRepo { (repo, _) =>
      val nonExistentId = UUID.randomUUID()
      repo.findByCorrelationId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- findByEventType ---

  test("findByEventType filters by event type within an execution") {
    withRepo { (repo, executionId) =>
      val event1 = TestExecutionEvents.statusChangeEvent(executionId = executionId)
      val event2 = TestExecutionEvents.logEvent(executionId = executionId)
      val event3 = TestExecutionEvents.metricEvent(executionId = executionId)
      val event4 = TestExecutionEvents.logEvent(executionId = executionId)

      for
        _       <- repo.create(event1)
        _       <- repo.create(event2)
        _       <- repo.create(event3)
        _       <- repo.create(event4)
        results <- repo.findByEventType(executionId, "log")
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 log events, got ${results.length}")
          assert(results.forall(_.eventType == "log"))
          assert(results.forall(_.executionId == executionId))
        }
      yield ()
    }
  }

  test("findByEventType returns empty for non-matching event type") {
    withRepo { (repo, executionId) =>
      val event = TestExecutionEvents.statusChangeEvent(executionId = executionId)

      for
        _       <- repo.create(event)
        results <- repo.findByEventType(executionId, "non_existent_type")
        _       <- IO(assert(results.isEmpty))
      yield ()
    }
  }

  test("findByEventType respects limit parameter") {
    withRepo { (repo, executionId) =>
      val events = (1 to 10).map(_ => TestExecutionEvents.logEvent(executionId = executionId))

      for
        _       <- events.toVector.traverse(repo.create(_))
        results <- repo.findByEventType(executionId, "log", limit = 5)
        _       <- IO(assert(results.length <= 5, s"Should return at most 5 events, got ${results.length}"))
      yield ()
    }
  }

  // --- ordering ---

  test("results are ordered by occurredAt descending") {
    withRepo { (repo, executionId) =>
      val events = (1 to 5).map(_ => TestExecutionEvents.event(executionId = executionId))

      for
        _       <- events.toVector.traverse(repo.create(_))
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          val timestamps = results.map(_.occurredAt)
          val sorted     = timestamps.sortWith((a, b) => a.isAfter(b) || a == b)
          assertEquals(timestamps, sorted, "Events should be sorted by occurredAt descending")
        }
      yield ()
    }
  }
