package com.verschraenkt.ci.storage.repository

import cats.effect.{ IO, Resource }
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.fixtures.{ DatabaseContainerFixture, TestDatabaseFixture, TestPipelines }
import munit.CatsEffectSuite

/** Integration tests for PipelineVersionsRepository with real PostgreSQL database
  *
  * These tests use Testcontainers to spin up a real PostgreSQL instance
  */
class PipelineVersionsRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  // Enable auto-migration for all tests
  autoMigrate(true)
  sharing(true)

  // Helper to provide repository instances
  private def withRepos[A](f: (PipelineRepository, PipelineVersionsRepository) => IO[A]): IO[A] =
    withDatabase { db =>
      val pipelineRepo = new PipelineRepository(db)
      val versionsRepo = new PipelineVersionsRepository(db)
      f(pipelineRepo, versionsRepo)
    }

  // Helper that wraps withRepos and ensures pipeline is saved before test logic
  // Passes the saved pipeline to the test function to avoid ID mismatches
  private def withRepoAndPipeline[A](pipeline: Pipeline, user: String = TestPipelines.testUser.unwrap)(
      f: (PipelineVersionsRepository, Pipeline) => IO[A]
  ): IO[A] =
    withRepos { (pipelineRepo, versionsRepo) =>
      for
        _      <- pipelineRepo.save(pipeline, user)
        result <- f(versionsRepo, pipeline)
      yield result
    }

  private def withRepo[A](f: PipelineVersionsRepository => IO[A]): IO[A] =
    withRepos { (_, versionsRepo) => f(versionsRepo) }

  // use this function to create a saved version before the test run
  // Note: This also creates the pipeline in the pipelines table to satisfy foreign key constraint
  private def savedVersionFixture(
      pipeline: Pipeline = TestPipelines.simplePipeline,
      version: Int = 1,
      changeSummary: String = "Initial version"
  ) = ResourceFunFixture(
    Resource.make(
      withRepos { (pipelineRepo, versionsRepo) =>
        val user = TestPipelines.testUser
        for
          // First save the pipeline to satisfy foreign key constraint
          _ <- pipelineRepo.save(pipeline, user.unwrap)
          // Then create the version
          _ <- versionsRepo.create(pipeline, version, user, changeSummary)
        yield (pipeline, version)
      }
    )(_ => IO.unit)
  )

  savedVersionFixture().test("findByIdAndVersion returns version when it exists") {
    case (pipeline, version) =>
      withRepo { repo =>
        for
          result <- repo.findByIdAndVersion(pipeline.id, version)
          _ <- IO {
            assert(result.isDefined)
            assertEquals(result.get.id, pipeline.id)
          }
        yield ()
      }
  }

  test("findByIdAndVersion returns None when version does not exist") {
    withRepo { repo =>
      val nonExistentPipelineId = TestPipelines.simplePipeline.id
      repo.findByIdAndVersion(nonExistentPipelineId, 1).map(p => assert(p.isEmpty))
    }
  }

  savedVersionFixture(TestPipelines.complexPipeline, 1).test(
    "findByIdAndVersion returns correct pipeline data for complex pipeline"
  ) { case (pipeline, version) =>
    withRepo { repo =>
      for
        result <- repo.findByIdAndVersion(pipeline.id, version)
        _ <- IO {
          assert(result.isDefined)
          val retrieved = result.get

          // Validate basic pipeline properties
          assertEquals(retrieved.id, pipeline.id)
          assertEquals(retrieved.labels, pipeline.labels)

          // Validate workflows structure
          assertEquals(retrieved.workflows.length, pipeline.workflows.length)
          assertEquals(retrieved.workflows.map(_.name), pipeline.workflows.map(_.name))

          // Validate jobs within workflows
          retrieved.workflows.toVector.foreach { workflow =>
            val originalWorkflow = pipeline.workflows.find(_.name == workflow.name).get
            assertEquals(workflow.jobs.length, originalWorkflow.jobs.length)
            assertEquals(workflow.jobs.map(_.id.value), originalWorkflow.jobs.map(_.id.value))

            // Validate steps within jobs
            workflow.jobs.toVector.foreach { job =>
              val originalJob = originalWorkflow.jobs.find(_.id == job.id).get
              assertEquals(job.steps.length, originalJob.steps.length)
              assertEquals(job.steps, originalJob.steps)
            }
          }
        }
      yield ()
    }
  }

  test("create inserts version into database") {
    withRepos { (pipelineRepo, versionsRepo) =>
      val pipeline      = TestPipelines.simplePipeline
      val user          = TestPipelines.testUser
      val changeSummary = "Initial version"

      for
        _      <- pipelineRepo.save(pipeline, user.unwrap)
        _      <- versionsRepo.create(pipeline, 1, user, changeSummary)
        result <- versionsRepo.findByIdAndVersion(pipeline.id, 1)
        _ <- IO {
          assert(result.isDefined)
          assertEquals(result.get.id, pipeline.id)
        }
      yield ()
    }
  }

  test("create with duplicate pipeline ID and version raises DuplicateKey error") {
    withRepos { (pipelineRepo, versionsRepo) =>
      val pipeline      = TestPipelines.simplePipeline
      val user          = TestPipelines.testUser
      val changeSummary = "Initial version"

      for
        _     <- pipelineRepo.save(pipeline, user.unwrap)
        _     <- versionsRepo.create(pipeline, 1, user, changeSummary)
        error <- versionsRepo.create(pipeline, 1, user, changeSummary).intercept[StorageError.DuplicateKey]
        _ <- IO {
          assertEquals(error.entity, "PipelineVersions")
          assertEquals(error.id, pipeline.id.value)
        }
      yield ()
    }
  }

  test("create persists JSON definition correctly") {
    withDatabase { db =>
      val pipelineRepo  = new PipelineRepository(db)
      val repo          = new PipelineVersionsRepository(db)
      val pipeline      = TestPipelines.pipelineWithLabels
      val user          = TestPipelines.testUser
      val changeSummary = "Initial version"

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, changeSummary)
        // Verify the data was persisted by querying directly
        _ <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT definition FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
            val rs = stmt.executeQuery()
            if rs.next() then
              val jsonStr = rs.getString("definition")
              assert(jsonStr.contains("test-workflow"))
              assert(jsonStr.contains("npm test"))
            else fail("Pipeline version not found in database")
            rs.close()
            stmt.close()
          finally conn.close()
        }
      yield ()
    }
  }

  test("create persists all metadata correctly") {
    withDatabase { db =>
      val pipelineRepo  = new PipelineRepository(db)
      val repo          = new PipelineVersionsRepository(db)
      val pipeline      = TestPipelines.pipelineWithLabels
      val user          = TestPipelines.testUser
      val changeSummary = "Initial version with labels"

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, changeSummary)
        _ <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              """SELECT version, created_by, change_summary, definition
                 FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"""
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
            val rs = stmt.executeQuery()
            if rs.next() then
              assertEquals(rs.getInt("version"), 1)
              assertEquals(rs.getString("created_by"), user.unwrap)
              assertEquals(rs.getString("change_summary"), changeSummary)
              val jsonStr = rs.getString("definition")
              assert(jsonStr.contains("test-workflow"))
              assert(jsonStr.contains("production"))
            else fail("Pipeline version not found in database")
            rs.close()
            stmt.close()
          finally conn.close()
        }
      yield ()
    }
  }

  test("create handles labels correctly") {
    withDatabase { db =>
      val pipelineRepo  = new PipelineRepository(db)
      val repo          = new PipelineVersionsRepository(db)
      val pipeline      = TestPipelines.pipelineWithLabels
      val user          = TestPipelines.testUser
      val changeSummary = "Initial version"

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, changeSummary)
        labels <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT definition FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
            val rs = stmt.executeQuery()
            val lbls =
              if rs.next() then
                val jsonStr = rs.getString("definition")
                // Verify labels are in the JSON definition
                assert(jsonStr.contains("production"))
                assert(jsonStr.contains("automated"))
                assert(jsonStr.contains("critical"))
                Set("production", "automated", "critical") // Return expected labels for assertion
              else fail("Pipeline version not found")
            rs.close()
            stmt.close()
            lbls
          finally conn.close()
        }
        _ <- IO(assertEquals(labels, Set("production", "automated", "critical")))
      yield ()
    }
  }

  test("create multiple versions for same pipeline") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>
      val user = TestPipelines.testUser

      for
        _  <- repo.create(pipeline, 1, user, "Version 1")
        _  <- repo.create(pipeline, 2, user, "Version 2")
        _  <- repo.create(pipeline, 3, user, "Version 3")
        v1 <- repo.findByIdAndVersion(pipeline.id, 1)
        v2 <- repo.findByIdAndVersion(pipeline.id, 2)
        v3 <- repo.findByIdAndVersion(pipeline.id, 3)
        _ <- IO {
          assert(v1.isDefined)
          assert(v2.isDefined)
          assert(v3.isDefined)
        }
      yield ()
    }
  }

  test("findAllVersionsByPipelineId returns all versions ordered by version desc") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 2, user, "Version 2")
        _        <- repo.create(pipeline, 3, user, "Version 3")
        _        <- repo.create(pipeline, 5, user, "Version 5")
        versions <- repo.findAllVersionsByPipelineId(pipeline.id)
        _ <- IO {
          assertEquals(versions.length, 4)
          assertEquals(versions, Seq(5, 3, 2, 1))
        }
      yield ()
    }
  }

  test("findAllVersionsByPipelineId returns empty seq when no versions exist") {
    withRepo { repo =>
      val nonExistentId = PipelineId("non-existent-pipeline")
      repo.findAllVersionsByPipelineId(nonExistentId).map { versions =>
        assert(versions.isEmpty)
      }
    }
  }

  test("findLatestVersionByPipelineId returns highest version number") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _      <- repo.create(pipeline, 1, user, "Version 1")
        _      <- repo.create(pipeline, 3, user, "Version 3")
        _      <- repo.create(pipeline, 2, user, "Version 2")
        _      <- repo.create(pipeline, 5, user, "Version 5")
        latest <- repo.findLatestVersionByPipelineId(pipeline.id)
        _ <- IO {
          assert(latest.isDefined)
          assertEquals(latest.get, 5)
        }
      yield ()
    }
  }

  test("findLatestVersionByPipelineId returns None when no versions exist") {
    withRepo { repo =>
      val nonExistentId = PipelineId("non-existent-pipeline")
      repo.findLatestVersionByPipelineId(nonExistentId).map { latest =>
        assert(latest.isEmpty)
      }
    }
  }

  test("findVersionsInRange returns versions within range") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 2, user, "Version 2")
        _        <- repo.create(pipeline, 3, user, "Version 3")
        _        <- repo.create(pipeline, 4, user, "Version 4")
        _        <- repo.create(pipeline, 5, user, "Version 5")
        _        <- repo.create(pipeline, 10, user, "Version 10")
        versions <- repo.findVersionsInRange(pipeline.id, 2, 5)
        _ <- IO {
          assert(versions.contains(2))
          assert(versions.contains(3))
          assert(versions.contains(4))
          assert(versions.contains(5))
          assert(!versions.contains(1))
          assert(!versions.contains(10))
        }
      yield ()
    }
  }

  test("findVersionsInRange returns empty seq when no versions in range") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 10, user, "Version 10")
        versions <- repo.findVersionsInRange(pipeline.id, 2, 9)
        _        <- IO(assert(versions.isEmpty))
      yield ()
    }
  }

  test("findVersionsInRange with single version range") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 5, user, "Version 5")
        versions <- repo.findVersionsInRange(pipeline.id, 5, 5)
        _ <- IO {
          assertEquals(versions.length, 1)
          assertEquals(versions.head, 5)
        }
      yield ()
    }
  }

  test("countVersionsByPipelineId returns correct count") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _     <- repo.create(pipeline, 1, user, "Version 1")
        _     <- repo.create(pipeline, 2, user, "Version 2")
        _     <- repo.create(pipeline, 3, user, "Version 3")
        count <- repo.countVersionsByPipelineId(pipeline.id)
        _     <- IO(assertEquals(count, 3))
      yield ()
    }
  }

  test("countVersionsByPipelineId returns 0 when no versions exist") {
    withRepo { repo =>
      val nonExistentId = PipelineId("non-existent-pipeline")
      repo.countVersionsByPipelineId(nonExistentId).map { count =>
        assertEquals(count, 0)
      }
    }
  }

  test("existsVersion returns true when version exists") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _      <- repo.create(pipeline, 1, user, "Version 1")
        _      <- repo.create(pipeline, 3, user, "Version 3")
        exists <- repo.existsVersion(pipeline.id, 1)
        _      <- IO(assertEquals(exists, true))
      yield ()
    }
  }

  test("existsVersion returns false when version does not exist") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _      <- repo.create(pipeline, 1, user, "Version 1")
        exists <- repo.existsVersion(pipeline.id, 2)
        _      <- IO(assertEquals(exists, false))
      yield ()
    }
  }

  test("existsVersion returns false when pipeline does not exist") {
    withRepo { repo =>
      val nonExistentId = PipelineId("non-existent-pipeline")
      repo.existsVersion(nonExistentId, 1).map { exists =>
        assertEquals(exists, false)
      }
    }
  }

  test("listVersionsPaginated returns correct page") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _     <- repo.create(pipeline, 1, user, "Version 1")
        _     <- repo.create(pipeline, 2, user, "Version 2")
        _     <- repo.create(pipeline, 3, user, "Version 3")
        _     <- repo.create(pipeline, 4, user, "Version 4")
        _     <- repo.create(pipeline, 5, user, "Version 5")
        page1 <- repo.listVersionsPaginated(pipeline.id, 0, 2)
        page2 <- repo.listVersionsPaginated(pipeline.id, 2, 2)
        page3 <- repo.listVersionsPaginated(pipeline.id, 4, 2)
        _ <- IO {
          assertEquals(page1.length, 2)
          assertEquals(page1, Seq(5, 4))
          assertEquals(page2.length, 2)
          assertEquals(page2, Seq(3, 2))
          assertEquals(page3.length, 1)
          assertEquals(page3, Seq(1))
        }
      yield ()
    }
  }

  test("listVersionsPaginated respects limit parameter") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- (1 to 10).toVector.traverse(v => repo.create(pipeline, v, user, s"Version $v"))
        versions <- repo.listVersionsPaginated(pipeline.id, 0, 5)
        _        <- IO(assertEquals(versions.length, 5))
      yield ()
    }
  }

  test("listVersionsPaginated returns empty seq when offset exceeds total") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 2, user, "Version 2")
        versions <- repo.listVersionsPaginated(pipeline.id, 10, 5)
        _        <- IO(assert(versions.isEmpty))
      yield ()
    }
  }

  test("listVersionsPaginated returns versions sorted by version desc") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _        <- repo.create(pipeline, 3, user, "Version 3")
        _        <- repo.create(pipeline, 1, user, "Version 1")
        _        <- repo.create(pipeline, 5, user, "Version 5")
        _        <- repo.create(pipeline, 2, user, "Version 2")
        versions <- repo.listVersionsPaginated(pipeline.id, 0, 10)
        _        <- IO(assertEquals(versions, Seq(5, 3, 2, 1)))
      yield ()
    }
  }

  test("create sets created_at timestamp") {
    withDatabase { db =>
      val pipelineRepo = new PipelineRepository(db)
      val repo         = new PipelineVersionsRepository(db)
      val pipeline     = TestPipelines.simplePipeline
      val user         = TestPipelines.testUser
      val before       = java.time.Instant.now()

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, "Initial version")
        _ <- IO.sleep(scala.concurrent.duration.Duration(10, "ms"))
        after = java.time.Instant.now()
        createdAt <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT created_at FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
            val rs = stmt.executeQuery()
            val ts = if rs.next() then rs.getTimestamp("created_at").toInstant else fail("Not found")
            rs.close()
            stmt.close()
            ts
          finally conn.close()
        }
        _ <- IO {
          assert(!createdAt.isBefore(before), "created_at should be after create call")
          assert(!createdAt.isAfter(after), "created_at should be before current time")
        }
      yield ()
    }
  }

  test("create tracks createdBy user correctly") {
    withDatabase { db =>
      val pipelineRepo = new PipelineRepository(db)
      val repo         = new PipelineVersionsRepository(db)
      val pipeline     = TestPipelines.simplePipeline
      val user         = TestPipelines.anotherUser

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, "Initial version")
        createdBy <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT created_by FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
            val rs   = stmt.executeQuery()
            val user = if rs.next() then rs.getString("created_by") else fail("Not found")
            rs.close()
            stmt.close()
            user
          finally conn.close()
        }
        _ <- IO(assertEquals(createdBy, user.unwrap))
      yield ()
    }
  }

  test("transaction rollback on error") {
    withDatabase { db =>
      val pipelineRepo = new PipelineRepository(db)
      val repo         = new PipelineVersionsRepository(db)
      val pipeline     = TestPipelines.simplePipeline
      val user         = TestPipelines.testUser

      for
        _ <- pipelineRepo.save(pipeline, user.unwrap)
        _ <- repo.create(pipeline, 1, user, "Version 1")
        // Try to create again - should fail
        result <- repo.create(pipeline, 1, user, "Version 1").attempt
        _      <- IO(assert(result.isLeft))
        // Verify only one row exists (transaction rolled back properly)
        count <- IO.blocking {
          val conn = db.database.source.createConnection()
          try
            val stmt = conn.prepareStatement(
              "SELECT COUNT(*) FROM pipeline_versions WHERE pipeline_id = ? AND version = ?"
            )
            stmt.setString(1, pipeline.id.value)
            stmt.setInt(2, 1)
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

  test("create then findByIdAndVersion returns same pipeline") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _         <- repo.create(pipeline, 1, user, "Initial version")
        retrieved <- repo.findByIdAndVersion(pipeline.id, 1)
        _ <- IO {
          assert(retrieved.isDefined, "Pipeline version should be found after create")
          assertEquals(retrieved.get.id, pipeline.id)
          assertEquals(retrieved.get.labels, pipeline.labels)
        }
      yield ()
    }
  }

  test("multiple pipelines with same version number") {
    withRepos { (pipelineRepo, versionsRepo) =>
      val pipeline1 = TestPipelines.simplePipeline
      val pipeline2 = TestPipelines.pipelineWithLabels
      val pipeline3 = TestPipelines.complexPipeline
      val user      = TestPipelines.testUser

      for
        _  <- pipelineRepo.save(pipeline1, user.unwrap)
        _  <- pipelineRepo.save(pipeline2, user.unwrap)
        _  <- pipelineRepo.save(pipeline3, user.unwrap)
        _  <- versionsRepo.create(pipeline1, 1, user, "Pipeline 1 Version 1")
        _  <- versionsRepo.create(pipeline2, 1, user, "Pipeline 2 Version 1")
        _  <- versionsRepo.create(pipeline3, 1, user, "Pipeline 3 Version 1")
        v1 <- versionsRepo.findByIdAndVersion(pipeline1.id, 1)
        v2 <- versionsRepo.findByIdAndVersion(pipeline2.id, 1)
        v3 <- versionsRepo.findByIdAndVersion(pipeline3.id, 1)
        _ <- IO {
          assert(v1.isDefined)
          assert(v2.isDefined)
          assert(v3.isDefined)
          assertEquals(v1.get.id, pipeline1.id)
          assertEquals(v2.get.id, pipeline2.id)
          assertEquals(v3.get.id, pipeline3.id)
        }
      yield ()
    }
  }

  test("findAllVersionsByPipelineId with multiple pipelines") {
    withRepos { (pipelineRepo, versionsRepo) =>
      val pipeline1 = TestPipelines.simplePipeline
      val pipeline2 = TestPipelines.pipelineWithLabels
      val user      = TestPipelines.testUser

      for
        _         <- pipelineRepo.save(pipeline1, user.unwrap)
        _         <- pipelineRepo.save(pipeline2, user.unwrap)
        _         <- versionsRepo.create(pipeline1, 1, user, "P1 V1")
        _         <- versionsRepo.create(pipeline1, 2, user, "P1 V2")
        _         <- versionsRepo.create(pipeline2, 1, user, "P2 V1")
        _         <- versionsRepo.create(pipeline2, 2, user, "P2 V2")
        _         <- versionsRepo.create(pipeline2, 3, user, "P2 V3")
        versions1 <- versionsRepo.findAllVersionsByPipelineId(pipeline1.id)
        versions2 <- versionsRepo.findAllVersionsByPipelineId(pipeline2.id)
        _ <- IO {
          assertEquals(versions1.length, 2)
          assertEquals(versions2.length, 3)
          assertEquals(versions1, Seq(2, 1))
          assertEquals(versions2, Seq(3, 2, 1))
        }
      yield ()
    }
  }

  test("countVersionsByPipelineId with large number of versions") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _     <- (1 to 100).toVector.traverse(v => repo.create(pipeline, v, user, s"Version $v"))
        count <- repo.countVersionsByPipelineId(pipeline.id)
        _     <- IO(assertEquals(count, 100))
      yield ()
    }
  }

  test("create with empty change summary") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user = TestPipelines.testUser

      for
        _      <- repo.create(pipeline, 1, user, "")
        result <- repo.findByIdAndVersion(pipeline.id, 1)
        _      <- IO(assert(result.isDefined))
      yield ()
    }
  }

  test("create with very long change summary") {
    withRepoAndPipeline(TestPipelines.simplePipeline) { (repo, pipeline) =>

      val user        = TestPipelines.testUser
      val longSummary = "A" * 1000

      for
        _      <- repo.create(pipeline, 1, user, longSummary)
        result <- repo.findByIdAndVersion(pipeline.id, 1)
        _      <- IO(assert(result.isDefined))
      yield ()
    }
  }
