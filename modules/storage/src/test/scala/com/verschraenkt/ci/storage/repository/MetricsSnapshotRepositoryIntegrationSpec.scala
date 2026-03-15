package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.{
  DatabaseContainerFixture,
  TestDatabaseFixture,
  TestExecutions,
  TestJobExecutions,
  TestMetricsSnapshots,
  TestWorkflowExecutions
}
import munit.CatsEffectSuite

import java.time.Instant
import java.time.temporal.ChronoUnit

class MetricsSnapshotRepositoryIntegrationSpec
    extends CatsEffectSuite
    with DatabaseContainerFixture
    with TestDatabaseFixture:

  autoMigrate(true)
  sharing(true)

  private val snowflakeProvider = SnowflakeProvider.make(77)
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
             VALUES (?, 1, '{"workflows":[]}'::jsonb, 'test-user', 'Seeded for metrics snapshot integration tests')
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
    val jobExec = TestJobExecutions
      .runningJobExecution(
        workflowExecutionId = wfExec.workflowExecutionId,
        executionId = execution.executionId
      )
      .copy(executorId = None, assignedAt = None)

    for
      _ <- executionRepo.create(execution)
      _ <- wfRepo.create(wfExec)
      _ <- jobRepo.create(jobExec)
    yield jobExec.jobExecutionId

  private def withSeededDatabase[A](f: DatabaseModule => IO[A]): IO[A] =
    withDatabase { db =>
      seedPipelineVersions(db) *> f(db)
    }

  private def withRepo[A](f: (MetricsSnapshotRepository, Long) => IO[A]): IO[A] =
    withSeededDatabase { db =>
      seedParentRecords(db).flatMap { jobExecutionId =>
        f(new MetricsSnapshotRepository(db), jobExecutionId)
      }
    }

  // --- create ---

  test("create inserts metrics snapshot into database") {
    withRepo { (repo, jobExecutionId) =>
      val snapshot = TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId)

      for
        _       <- repo.create(snapshot)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.nonEmpty, "Should find the created snapshot")
          assertEquals(results.head.jobExecutionId, jobExecutionId)
          assert(results.head.cpuUsageMilli.contains(500))
        }
      yield ()
    }
  }

  test("create multiple snapshots for the same job execution") {
    withRepo { (repo, jobExecutionId) =>
      val snap1 = TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId)
      val snap2 = TestMetricsSnapshots.highUsageSnapshot(jobExecutionId = jobExecutionId)
      val snap3 = TestMetricsSnapshots.emptySnapshot(jobExecutionId = jobExecutionId)

      for
        _       <- repo.create(snap1)
        _       <- repo.create(snap2)
        _       <- repo.create(snap3)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.length >= 3, s"Should have at least 3 snapshots, got ${results.length}")
        }
      yield ()
    }
  }

  test("create snapshot with only CPU metrics") {
    withRepo { (repo, jobExecutionId) =>
      val snapshot =
        TestMetricsSnapshots.cpuOnlySnapshot(jobExecutionId = jobExecutionId, cpuUsageMilli = 4000)

      for
        _       <- repo.create(snapshot)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.nonEmpty)
          val r = results.head
          assert(r.cpuUsageMilli.contains(4000))
          assertEquals(r.memoryUsageMib, None)
          assertEquals(r.diskUsageMib, None)
          assertEquals(r.networkRxBytes, None)
          assertEquals(r.networkTxBytes, None)
        }
      yield ()
    }
  }

  test("create snapshot with all fields empty") {
    withRepo { (repo, jobExecutionId) =>
      val snapshot = TestMetricsSnapshots.emptySnapshot(jobExecutionId = jobExecutionId)

      for
        _       <- repo.create(snapshot)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.nonEmpty)
          val r = results.head
          assertEquals(r.cpuUsageMilli, None)
          assertEquals(r.memoryUsageMib, None)
          assertEquals(r.diskUsageMib, None)
          assertEquals(r.networkRxBytes, None)
          assertEquals(r.networkTxBytes, None)
        }
      yield ()
    }
  }

  // --- findByJobExecutionId ---

  test("findByJobExecutionId returns snapshots for the given job execution") {
    withRepo { (repo, jobExecutionId) =>
      val snap1 = TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId)
      val snap2 = TestMetricsSnapshots.highUsageSnapshot(jobExecutionId = jobExecutionId)

      for
        _       <- repo.create(snap1)
        _       <- repo.create(snap2)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.length >= 2)
          assert(results.forall(_.jobExecutionId == jobExecutionId))
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

  test("findByJobExecutionId respects limit parameter") {
    withRepo { (repo, jobExecutionId) =>
      val snapshots = (1 to 10).map(_ => TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId))

      for
        _       <- snapshots.toVector.traverse(repo.create(_))
        results <- repo.findByJobExecutionId(jobExecutionId, limit = 5)
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 snapshots, got ${results.length}"))
      yield ()
    }
  }

  test("findByJobExecutionId returns results ordered by timestamp descending") {
    withRepo { (repo, jobExecutionId) =>
      val snapshots = (1 to 5).map(_ => TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId))

      for
        _       <- snapshots.toVector.traverse(repo.create(_))
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          val timestamps = results.map(_.timestamp)
          val sorted     = timestamps.sortWith((a, b) => a.isAfter(b) || a == b)
          assertEquals(timestamps, sorted, "Snapshots should be sorted by timestamp descending")
        }
      yield ()
    }
  }

  // --- findByJobExecutionIdInRange ---

  test("findByJobExecutionIdInRange returns snapshots within the time range") {
    withRepo { (repo, jobExecutionId) =>
      val now    = Instant.now()
      val past   = now.minus(1, ChronoUnit.HOURS)
      val future = now.plus(1, ChronoUnit.HOURS)

      val inRange = TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId, timestamp = now)
      val outRange = TestMetricsSnapshots.snapshot(
        jobExecutionId = jobExecutionId,
        timestamp = now.minus(2, ChronoUnit.HOURS)
      )

      for
        _       <- repo.create(inRange)
        _       <- repo.create(outRange)
        results <- repo.findByJobExecutionIdInRange(jobExecutionId, past, future)
        _ <- IO {
          assert(results.nonEmpty, "Should find snapshots in range")
          assert(results.forall(r => !r.timestamp.isBefore(past) && !r.timestamp.isAfter(future)))
        }
      yield ()
    }
  }

  test("findByJobExecutionIdInRange returns empty when no snapshots in range") {
    withRepo { (repo, jobExecutionId) =>
      val now      = Instant.now()
      val snapshot = TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId, timestamp = now)

      for
        _ <- repo.create(snapshot)
        results <- repo.findByJobExecutionIdInRange(
          jobExecutionId,
          now.plus(1, ChronoUnit.HOURS),
          now.plus(2, ChronoUnit.HOURS)
        )
        _ <- IO(assert(results.isEmpty, "Should find no snapshots outside range"))
      yield ()
    }
  }

  test("findByJobExecutionIdInRange respects limit parameter") {
    withRepo { (repo, jobExecutionId) =>
      val now = Instant.now()
      val snapshots =
        (1 to 10).map(_ => TestMetricsSnapshots.snapshot(jobExecutionId = jobExecutionId, timestamp = now))

      for
        _ <- snapshots.toVector.traverse(repo.create(_))
        results <- repo.findByJobExecutionIdInRange(
          jobExecutionId,
          now.minus(1, ChronoUnit.HOURS),
          now.plus(1, ChronoUnit.HOURS),
          limit = 5
        )
        _ <- IO(assert(results.length <= 5, s"Should return at most 5 snapshots, got ${results.length}"))
      yield ()
    }
  }

  // --- findLatest ---

  test("findLatest returns the most recent snapshot") {
    withRepo { (repo, jobExecutionId) =>
      val now = Instant.now()
      val older = TestMetricsSnapshots.snapshot(
        jobExecutionId = jobExecutionId,
        timestamp = now.minus(1, ChronoUnit.HOURS)
      )
      val newest =
        TestMetricsSnapshots.highUsageSnapshot(jobExecutionId = jobExecutionId).copy(timestamp = now)

      for
        _      <- repo.create(older)
        _      <- repo.create(newest)
        result <- repo.findLatest(jobExecutionId)
        _ <- IO {
          assert(result.isDefined, "Should find the latest snapshot")
          assert(result.get.cpuUsageMilli.contains(8000), "Should return the high-usage snapshot")
        }
      yield ()
    }
  }

  test("findLatest returns None when no snapshots exist") {
    withRepo { (repo, _) =>
      val nonExistentId = getNextSnowflake.value
      repo.findLatest(nonExistentId).map(result => assert(result.isEmpty))
    }
  }

  // --- persists all fields correctly ---

  test("create persists all fields correctly") {
    withRepo { (repo, jobExecutionId) =>
      val snapshot = TestMetricsSnapshots.snapshot(
        jobExecutionId = jobExecutionId,
        cpuUsageMilli = Some(3000),
        memoryUsageMib = Some(4096),
        diskUsageMib = Some(20480),
        networkRxBytes = Some(5242880L),
        networkTxBytes = Some(2621440L)
      )

      for
        _       <- repo.create(snapshot)
        results <- repo.findByJobExecutionId(jobExecutionId)
        _ <- IO {
          assert(results.nonEmpty)
          val r = results.head
          assertEquals(r.jobExecutionId, jobExecutionId)
          assert(r.cpuUsageMilli.contains(3000))
          assert(r.memoryUsageMib.contains(4096))
          assert(r.diskUsageMib.contains(20480))
          assert(r.networkRxBytes.contains(5242880L))
          assert(r.networkTxBytes.contains(2621440L))
        }
      yield ()
    }
  }
