package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.fixtures.{ DatabaseContainerFixture, TestDatabaseFixture, TestPipelines }
import munit.CatsEffectSuite

/** Integration tests for PipelineRepository with real PostgreSQL database
  *
  * These tests use Testcontainers to spin up a real PostgreSQL instance
  */
class PipelineRepositoryIntegrationSpec extends DatabaseContainerFixture with TestDatabaseFixture:
  self: CatsEffectSuite =>

  test("save inserts pipeline into database") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.simplePipeline
        val user     = TestPipelines.testUser

        for
          savedId <- repo.save(pipeline, user.unwrap)
          _       <- IO(assertEquals(savedId, pipeline.id))
        yield ()
      }
    }
  }

  test("save with duplicate ID raises DuplicateKey error") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.simplePipeline
        val user     = TestPipelines.testUser

        for
          _      <- repo.save(pipeline, user.unwrap)
          result <- repo.save(pipeline, user.unwrap).attempt
          _ <- IO {
            assert(result.isLeft)
            result.left.foreach { error =>
              assert(error.isInstanceOf[StorageError.DuplicateKey])
              val dupError = error.asInstanceOf[StorageError.DuplicateKey]
              assertEquals(dupError.entity, "Pipeline")
              assertEquals(dupError.id, pipeline.id.value)
            }
          }
        yield ()
      }
    }
  }

  test("save persists JSON definition correctly") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.pipelineWithLabels
        val user     = TestPipelines.testUser

        for
          _ <- repo.save(pipeline, user.unwrap)
          // Verify the data was persisted by querying directly
          _ <- IO.blocking {
            val conn = db.database.source.createConnection()
            try
              val stmt = conn.prepareStatement(
                "SELECT definition FROM pipelines WHERE pipeline_id = ?"
              )
              stmt.setString(1, pipeline.id.value)
              val rs = stmt.executeQuery()
              if rs.next() then
                val jsonStr = rs.getString("definition")
                assert(jsonStr.contains("test-workflow"))
                assert(jsonStr.contains("npm test"))
              else fail("Pipeline not found in database")
              rs.close()
              stmt.close()
            finally conn.close()
          }
        yield ()
      }
    }
  }

  test("save persists all metadata correctly") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.pipelineWithLabels
        val user     = TestPipelines.testUser

        for
          _ <- repo.save(pipeline, user.unwrap)
          _ <- IO.blocking {
            val conn = db.database.source.createConnection()
            try
              val stmt = conn.prepareStatement(
                """SELECT name, labels, version, created_by, is_active, deleted_at
                   FROM pipelines WHERE pipeline_id = ?"""
              )
              stmt.setString(1, pipeline.id.value)
              val rs = stmt.executeQuery()
              if rs.next() then
                assertEquals(rs.getString("name"), "test-workflow")
                assertEquals(rs.getInt("version"), 1)
                assertEquals(rs.getString("created_by"), user.unwrap)
                assertEquals(rs.getBoolean("is_active"), true)
                assertEquals(rs.getTimestamp("deleted_at"), null)
              else fail("Pipeline not found in database")
              rs.close()
              stmt.close()
            finally conn.close()
          }
        yield ()
      }
    }
  }

  test("save creates correct version number") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.simplePipeline
        val user     = TestPipelines.testUser

        for
          _ <- repo.save(pipeline, user.unwrap)
          version <- IO.blocking {
            val conn = db.database.source.createConnection()
            try
              val stmt = conn.prepareStatement(
                "SELECT version FROM pipelines WHERE pipeline_id = ?"
              )
              stmt.setString(1, pipeline.id.value)
              val rs = stmt.executeQuery()
              val v =
                if rs.next() then rs.getInt("version")
                else fail("Pipeline not found")
              rs.close()
              stmt.close()
              v
            finally conn.close()
          }
          _ <- IO(assertEquals(version, 1))
        yield ()
      }
    }
  }

  test("save handles labels correctly") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.pipelineWithLabels
        val user     = TestPipelines.testUser

        for
          _ <- repo.save(pipeline, user.unwrap)
          labels <- IO.blocking {
            val conn = db.database.source.createConnection()
            try
              val stmt = conn.prepareStatement(
                "SELECT labels FROM pipelines WHERE pipeline_id = ?"
              )
              stmt.setString(1, pipeline.id.value)
              val rs = stmt.executeQuery()
              val lbls =
                if rs.next() then
                  val arr    = rs.getArray("labels")
                  val labels = arr.getArray.asInstanceOf[Array[String]].toSet
                  arr.free()
                  labels
                else fail("Pipeline not found")
              rs.close()
              stmt.close()
              lbls
            finally conn.close()
          }
          _ <- IO(assertEquals(labels, Set("production", "automated", "critical")))
        yield ()
      }
    }
  }

  test("transaction rollback on error") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo     = new PipelineRepository(db)
        val pipeline = TestPipelines.simplePipeline
        val user     = TestPipelines.testUser

        for
          _ <- repo.save(pipeline, user.unwrap)
          // Try to save again - should fail
          result <- repo.save(pipeline, user.unwrap).attempt
          _      <- IO(assert(result.isLeft))
          // Verify only one row exists (transaction rolled back properly)
          count <- IO.blocking {
            val conn = db.database.source.createConnection()
            try
              val stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM pipelines WHERE pipeline_id = ?"
              )
              stmt.setString(1, pipeline.id.value)
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
  }

  test("save multiple different pipelines") {
    withDatabase { dbModule =>
      withMigratedDatabase(dbModule) { db =>
        val repo      = new PipelineRepository(db)
        val pipeline1 = TestPipelines.simplePipeline
        val pipeline2 = TestPipelines.pipelineWithLabels
        val pipeline3 = TestPipelines.complexPipeline
        val user      = TestPipelines.testUser

        for
          id1 <- repo.save(pipeline1, user.unwrap)
          id2 <- repo.save(pipeline2, user.unwrap)
          id3 <- repo.save(pipeline3, user.unwrap)
          _ <- IO {
            assertEquals(id1, pipeline1.id)
            assertEquals(id2, pipeline2.id)
            assertEquals(id3, pipeline3.id)
          }
        yield ()
      }
    }
  }
