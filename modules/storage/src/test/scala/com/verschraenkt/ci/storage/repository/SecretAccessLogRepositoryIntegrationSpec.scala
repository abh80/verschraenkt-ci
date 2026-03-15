package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.*
import munit.CatsEffectSuite

import java.util.UUID

class SecretAccessLogRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val seededPipelineIds: Vector[String] =
    Vector("test-pipeline") ++ (1 to 500).map(i => s"pipeline-$i")

  private def withRepo[A](f: (SecretAccessLogRepository, UUID, Long, UUID) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedParentRecords(db).flatMap { case (secretId, jobExecutionId, executorId) =>
        f(new SecretAccessLogRepository(db), secretId, jobExecutionId, executorId)
      }
    }

  /** Seeds pipeline, execution, workflow execution, job execution, and executor; returns (secretId,
    * jobExecutionId, executorId)
    */
  private def seedParentRecords(db: DatabaseModule): IO[(UUID, Long, UUID)] =
    val executionRepo = new ExecutionRepository(db)
    val wfRepo        = new WorkflowExecutionRepository(db)
    val jobRepo       = new JobExecutionRepository(db)
    val executorRepo  = new ExecutorRepository(db)
    val secretRepo    = new SecretRepository(db)

    val execution = TestExecutions.queuedExecution()
    val wfExec    = TestWorkflowExecutions.queuedWorkflowExecution(executionId = execution.executionId)
    val jobExec = TestJobExecutions.queuedJobExecution(
      workflowExecutionId = wfExec.workflowExecutionId,
      executionId = execution.executionId
    )
    val executor = TestExecutors.newExecutor()
    val secret   = TestSecrets.secret()

    for
      _          <- executionRepo.create(execution)
      _          <- wfRepo.create(wfExec)
      _          <- jobRepo.create(jobExec)
      executorId <- executorRepo.save(executor)
      secretId   <- secretRepo.create(secret)
    yield (secretId, jobExec.jobExecutionId, executorId)

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for secret access log tests')
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

  // --- create ---

  test("create inserts access log entry and returns generated ID") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val entry = TestSecrets.accessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        logId   <- repo.create(entry)
        results <- repo.findBySecretId(secretId)
        _ <- IO {
          assert(results.exists(_.logId.contains(logId)))
          val log = results.find(_.logId.contains(logId)).get
          assertEquals(log.secretId, secretId)
          assertEquals(log.jobExecutionId, jobExecutionId)
          assertEquals(log.executorId, executorId)
          assertEquals(log.granted, true)
        }
      yield ()
    }
  }

  test("create persists denied access log") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val entry = TestSecrets.deniedAccessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId,
        reason = "Not authorized"
      )

      for
        logId   <- repo.create(entry)
        results <- repo.findBySecretId(secretId)
        _ <- IO {
          val log = results.find(_.logId.contains(logId)).get
          assertEquals(log.granted, false)
          assertEquals(log.denialReason, Some("Not authorized"))
        }
      yield ()
    }
  }

  // --- findBySecretId ---

  test("findBySecretId returns all logs for a secret") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val entry1 =
        TestSecrets.accessLog(secretId = secretId, jobExecutionId = jobExecutionId, executorId = executorId)
      val entry2 =
        TestSecrets.deniedAccessLog(
          secretId = secretId,
          jobExecutionId = jobExecutionId,
          executorId = executorId
        )

      for
        _       <- repo.create(entry1)
        _       <- repo.create(entry2)
        results <- repo.findBySecretId(secretId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 log entries, got ${results.length}")
          assert(results.forall(_.secretId == secretId))
        }
      yield ()
    }
  }

  test("findBySecretId returns empty when no logs exist") {
    withRepo { (repo, _, _, _) =>
      repo.findBySecretId(UUID.randomUUID()).map(results => assert(results.isEmpty))
    }
  }

  // --- findByJobExecutionId ---

  test("findByJobExecutionId returns all logs for a job execution") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val entry = TestSecrets.accessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        _       <- repo.create(entry)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.nonEmpty)
          assert(results.forall(_.jobExecutionId == jobExecutionId))
        }
      yield ()
    }
  }

  test("findByJobExecutionId returns empty for non-existent job execution") {
    withRepo { (repo, _, _, _) =>
      repo.findByJobExecutionId(999999999L).map(results => assert(results.isEmpty))
    }
  }

  // --- findDenied ---

  test("findDenied returns only denied access attempts") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val granted = TestSecrets.accessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )
      val denied = TestSecrets.deniedAccessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )

      for
        _        <- repo.create(granted)
        deniedId <- repo.create(denied)
        results  <- repo.findDenied()
        _ <- IO {
          assert(results.nonEmpty, "Should have at least 1 denied entry")
          assert(results.forall(!_.granted), "All returned entries should be denied")
          assert(results.exists(_.logId.contains(deniedId)))
        }
      yield ()
    }
  }

  test("findDenied respects limit parameter") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val entries = (1 to 10).map(i =>
        TestSecrets.deniedAccessLog(
          secretId = secretId,
          jobExecutionId = jobExecutionId,
          executorId = executorId,
          reason = s"Reason $i"
        )
      )

      for
        _       <- entries.toVector.foldLeft(IO.unit)((acc, e) => acc *> repo.create(e).void)
        results <- repo.findDenied(limit = 5)
        _       <- IO(assert(results.length <= 5, s"Should return at most 5 entries, got ${results.length}"))
      yield ()
    }
  }

  // --- lifecycle test ---

  test("complete access log lifecycle") {
    withRepo { (repo, secretId, jobExecutionId, executorId) =>
      val grantedEntry = TestSecrets.accessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId
      )
      val deniedEntry = TestSecrets.deniedAccessLog(
        secretId = secretId,
        jobExecutionId = jobExecutionId,
        executorId = executorId,
        reason = "Scope violation"
      )

      for
        // Log granted access
        grantedId <- repo.create(grantedEntry)

        // Log denied access
        deniedId <- repo.create(deniedEntry)

        // Find all by secret
        bySecret <- repo.findBySecretId(secretId)
        _ <- IO {
          assert(bySecret.length >= 2)
          assert(bySecret.exists(_.logId.contains(grantedId)))
          assert(bySecret.exists(_.logId.contains(deniedId)))
        }

        // Find by job execution
        byJob <- repo.findByJobExecutionId(jobExecutionId)
        _     <- IO(assert(byJob.length >= 2))

        // Find denied only
        denied <- repo.findDenied()
        _ <- IO {
          assert(denied.exists(_.logId.contains(deniedId)))
          assert(
            !denied.exists(_.logId.contains(grantedId)),
            "Granted entry should not appear in denied list"
          )
        }
      yield ()
    }
  }
