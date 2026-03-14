package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestArtifacts,
  TestDatabaseFixture,
  TestExecutions,
  TestJobExecutions,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.util.UUID

class ArtifactRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(79)
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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for artifact integration tests')
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

  /** Seeds another job execution under the same parent chain */
//  private def seedAnotherJobExecution(db: DatabaseModule, executionId: Long): IO[Long] =
//    val wfRepo  = new WorkflowExecutionRepository(db)
//    val jobRepo = new JobExecutionRepository(db)
//    val wfExec  = TestWorkflowExecutions.queuedWorkflowExecution(executionId = executionId)
//    val jobExec = TestJobExecutions.queuedJobExecution(
//      workflowExecutionId = wfExec.workflowExecutionId,
//      executionId = executionId
//    )
//
//    for
//      _ <- wfRepo.create(wfExec)
//      _ <- jobRepo.create(jobExec)
//    yield jobExec.jobExecutionId

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (ArtifactRepository, Long, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedParentRecords(db).flatMap { case (executionId, jobExecutionId) =>
        f(new ArtifactRepository(db), executionId, jobExecutionId)
      }
    }

  // --- create ---

  test("create inserts artifact and returns generated ID") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id     <- repo.create(artifact)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.jobExecutionId, jobExecutionId)
          assertEquals(result.get.executionId, executionId)
          assertEquals(result.get.name, artifact.name)
        }
      yield ()
    }
  }

  // --- findById ---

  test("findById returns artifact when it exists") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id     <- repo.create(artifact)
        result <- repo.findById(id)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.artifactId, Some(id))
          assertEquals(result.get.name, artifact.name)
          assertEquals(result.get.path, artifact.path)
          assertEquals(result.get.storageBackend, artifact.storageBackend)
          assertEquals(result.get.storageKey, artifact.storageKey)
          assertEquals(result.get.storageBucket, artifact.storageBucket)
          assertEquals(result.get.sizeBytes, artifact.sizeBytes)
        }
      yield ()
    }
  }

  test("findById returns None when artifact does not exist") {
    withRepo { (repo, _, _) =>
      repo.findById(UUID.randomUUID()).map(r => assert(r.isEmpty))
    }
  }

  // --- findByJobExecutionId ---

  test("findByJobExecutionId returns all artifacts for a job") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val art1 =
        TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId, name = "art-1")
      val art2 =
        TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId, name = "art-2")

      for
        _       <- repo.create(art1)
        _       <- repo.create(art2)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.length >= 2, s"Should have at least 2 artifacts, got ${results.length}")
          assert(results.forall(_.jobExecutionId == jobExecutionId))
        }
      yield ()
    }
  }

  test("findByJobExecutionId returns empty when no artifacts exist") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByJobExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- findByExecutionId ---

  test("findByExecutionId returns all artifacts for an execution") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val art1 =
        TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId, name = "exec-1")
      val art2 =
        TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId, name = "exec-2")
      val art3 =
        TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId, name = "exec-3")

      for
        _       <- repo.create(art1)
        _       <- repo.create(art2)
        _       <- repo.create(art3)
        results <- repo.findByExecutionId(executionId)
        _ <- IO {
          assert(results.length >= 3, s"Should have at least 3 artifacts, got ${results.length}")
          assert(results.forall(_.executionId == executionId))
        }
      yield ()
    }
  }

  test("findByExecutionId respects limit parameter") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifacts =
        (1 to 10).map(_ => TestArtifacts.artifact(jobExecutionId = jobExecutionId, executionId = executionId))

      for
        _       <- artifacts.toVector.traverse(repo.create(_))
        results <- repo.findByExecutionId(executionId, limit = 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 artifacts, got ${results.length}"))
      yield ()
    }
  }

  test("findByExecutionId returns empty for non-existent execution") {
    withRepo { (repo, _, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findByExecutionId(nonExistentId).map(results => assert(results.isEmpty))
    }
  }

  // --- findPublicByExecutionId ---

  test("findPublicByExecutionId returns only public artifacts") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val privateArt = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId,
        name = "private-art"
      )
      val publicArt = TestArtifacts.publicArtifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId,
        name = "public-art"
      )

      for
        _        <- repo.create(privateArt)
        publicId <- repo.create(publicArt)
        results  <- repo.findPublicByExecutionId(executionId)
        _ <- IO {
          assert(results.nonEmpty, "Should have at least 1 public artifact")
          assert(results.forall(_.isPublic), "All returned artifacts should be public")
          assert(results.exists(_.artifactId.contains(publicId)))
        }
      yield ()
    }
  }

  // --- findExpired ---

  test("findExpired returns artifacts past their expiry date") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val expired = TestArtifacts.expiredArtifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )
      val notExpired = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        expiredId    <- repo.create(expired)
        notExpiredId <- repo.create(notExpired)
        results      <- repo.findExpired()
        _ <- IO {
          assert(
            results.exists(_.artifactId.contains(expiredId)),
            "Should include expired artifact"
          )
          assert(
            !results.exists(_.artifactId.contains(notExpiredId)),
            "Should not include non-expired artifact"
          )
        }
      yield ()
    }
  }

  test("findExpired respects limit parameter") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val expiredArtifacts =
        (1 to 10).map(_ =>
          TestArtifacts.expiredArtifact(jobExecutionId = jobExecutionId, executionId = executionId)
        )

      for
        _       <- expiredArtifacts.toVector.traverse(repo.create(_))
        results <- repo.findExpired(limit = 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 artifacts, got ${results.length}"))
      yield ()
    }
  }

  // --- makePublic ---

  test("makePublic makes a private artifact public") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id        <- repo.create(artifact)
        _         <- repo.makePublic(id, "test-user")
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.isPublic, true)
          assert(retrieved.get.madePublicAt.isDefined)
          assertEquals(retrieved.get.madePublicBy, Some("test-user"))
        }
      yield ()
    }
  }

  test("makePublic returns true when successful") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id     <- repo.create(artifact)
        result <- repo.makePublic(id, "test-user")
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("makePublic returns false when artifact not found") {
    withRepo { (repo, _, _) =>
      repo.makePublic(UUID.randomUUID(), "test-user").map(result => assertEquals(result, false))
    }
  }

  // --- delete ---

  test("delete removes the artifact") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id     <- repo.create(artifact)
        _      <- repo.delete(id)
        result <- repo.findById(id)
        _      <- IO(assert(result.isEmpty, "Deleted artifact should not be found"))
      yield ()
    }
  }

  test("delete returns true when successful") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId
      )

      for
        id     <- repo.create(artifact)
        result <- repo.delete(id)
        _      <- IO(assertEquals(result, true))
      yield ()
    }
  }

  test("delete returns false when artifact not found") {
    withRepo { (repo, _, _) =>
      repo.delete(UUID.randomUUID()).map(result => assertEquals(result, false))
    }
  }

  // --- lifecycle test ---

  test("complete artifact lifecycle") {
    withRepo { (repo, executionId, jobExecutionId) =>
      val artifact = TestArtifacts.artifact(
        jobExecutionId = jobExecutionId,
        executionId = executionId,
        name = "lifecycle-artifact"
      )

      for
        // Upload artifact
        id        <- repo.create(artifact)
        retrieved <- repo.findById(id)
        _ <- IO {
          assert(retrieved.isDefined)
          assertEquals(retrieved.get.isPublic, false)
          assertEquals(retrieved.get.name, "lifecycle-artifact")
        }

        // Find by job
        byJob <- repo.findByJobExecutionId(jobExecutionId)
        _     <- IO(assert(byJob.exists(_.artifactId.contains(id))))

        // Find by execution
        byExec <- repo.findByExecutionId(executionId)
        _      <- IO(assert(byExec.exists(_.artifactId.contains(id))))

        // Make public
        _         <- repo.makePublic(id, "admin")
        retrieved <- repo.findById(id)
        _ <- IO {
          assertEquals(retrieved.get.isPublic, true)
          assertEquals(retrieved.get.madePublicBy, Some("admin"))
        }

        // Verify it shows up in public query
        publicResults <- repo.findPublicByExecutionId(executionId)
        _             <- IO(assert(publicResults.exists(_.artifactId.contains(id))))

        // Delete
        _       <- repo.delete(id)
        deleted <- repo.findById(id)
        _       <- IO(assert(deleted.isEmpty))
      yield ()
    }
  }
