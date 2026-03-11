package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.Enums.{ Architecture, ExecutorStatus, Platform }
import com.verschraenkt.ci.storage.fixtures.TestExecutors
import io.circe.Json
import munit.FunSuite

import java.time.Instant

class ExecutorTableSpec extends FunSuite:

  test("ExecutorRow creates correct row with all fields") {
    val row = TestExecutors.onlineExecutor()

    assert(row.name.startsWith("executor-online-"))
    assertEquals(row.status, ExecutorStatus.Online)
    assertEquals(row.platform, Platform.Linux)
    assertEquals(row.architecture, Architecture.X86_64)
    assert(row.executorId.isDefined)
    assert(row.hostname.isDefined)
  }

  test("ExecutorRow handles online status") {
    val row = TestExecutors.onlineExecutor()

    assertEquals(row.status, ExecutorStatus.Online)
    assert(row.lastHeartbeat.isAfter(Instant.now().minusSeconds(60)))
  }

  test("ExecutorRow handles offline status") {
    val row = TestExecutors.offlineExecutor()

    assertEquals(row.status, ExecutorStatus.Offline)
    assertEquals(row.architecture, Architecture.Arm64)
  }

  test("ExecutorRow handles draining status") {
    val row = TestExecutors.drainingExecutor()

    assertEquals(row.status, ExecutorStatus.Draining)
    assertEquals(row.gpuCount, 2)
    assert(row.labels.contains("gpu"))
  }

  test("ExecutorRow handles new executor without ID") {
    val row = TestExecutors.newExecutor()

    assertEquals(row.executorId, None)
    assertEquals(row.hostname, None)
    assertEquals(row.lastJobAt, None)
    assertEquals(row.version, None)
  }

  test("ExecutorRow handles deleted executor") {
    val row = TestExecutors.deletedExecutor()

    assert(row.deletedAt.isDefined)
    assertEquals(row.platform, Platform.MacOS)
    assertEquals(row.architecture, Architecture.Arm64)
  }

  test("ExecutorRow handles resource fields") {
    val row = TestExecutors.drainingExecutor()

    assertEquals(row.cpuMillis, 16000)
    assertEquals(row.memoryMibs, 32768)
    assertEquals(row.gpuCount, 2)
    assertEquals(row.diskMibs, 204800)
  }

  test("ExecutorRow handles labels") {
    val row = TestExecutors.onlineExecutor()

    assert(row.labels.contains("linux"))
    assert(row.labels.contains("x86_64"))
    assert(row.labels.contains("general"))
  }

  test("ExecutorRow handles metadata as JSON") {
    val row = TestExecutors.onlineExecutor()

    assert(row.metadata.isObject)
    assert(row.metadata.hcursor.get[String]("region").toOption.contains("us-east-1"))
  }

  test("ExecutorRow handles empty metadata") {
    val row = TestExecutors.offlineExecutor()

    assert(row.metadata.isObject)
    assertEquals(row.metadata, Json.obj())
  }

  test("ExecutorRow handles different platforms") {
    val linux   = TestExecutors.onlineExecutor()
    val windows = TestExecutors.newExecutor()
    val macos   = TestExecutors.deletedExecutor()

    assertEquals(linux.platform, Platform.Linux)
    assertEquals(windows.platform, Platform.Windows64b)
    assertEquals(macos.platform, Platform.MacOS)
  }

  test("ExecutorRow handles different architectures") {
    val x86_64 = TestExecutors.onlineExecutor()
    val arm64  = TestExecutors.offlineExecutor()

    assertEquals(x86_64.architecture, Architecture.X86_64)
    assertEquals(arm64.architecture, Architecture.Arm64)
  }

  test("ExecutorRow handles different executor statuses") {
    val online   = TestExecutors.withStatus(ExecutorStatus.Online)
    val offline  = TestExecutors.withStatus(ExecutorStatus.Offline)
    val draining = TestExecutors.withStatus(ExecutorStatus.Draining)

    assertEquals(online.status, ExecutorStatus.Online)
    assertEquals(offline.status, ExecutorStatus.Offline)
    assertEquals(draining.status, ExecutorStatus.Draining)
  }

  test("ExecutorRow handles lastJobAt field") {
    val withJob    = TestExecutors.onlineExecutor()
    val withoutJob = TestExecutors.newExecutor()

    assert(withJob.lastJobAt.isDefined)
    assertEquals(withoutJob.lastJobAt, None)
  }

  test("ExecutorRow handles version field") {
    val withVersion    = TestExecutors.onlineExecutor()
    val withoutVersion = TestExecutors.newExecutor()

    assertEquals(withVersion.version, Some("1.0.0"))
    assertEquals(withoutVersion.version, None)
  }

  test("ExecutorRow default values for optional fields") {
    val row = ExecutorRow(
      executorId = None,
      name = "test",
      hostname = None,
      platform = Platform.Linux,
      architecture = Architecture.X86_64,
      cpuMillis = 1000,
      memoryMibs = 1024,
      gpuCount = 0,
      diskMibs = 10240,
      labels = List.empty,
      status = ExecutorStatus.Online,
      registeredAt = Instant.now(),
      lastHeartbeat = Instant.now(),
      lastJobAt = None,
      tokenHash = "sha256:test",
      version = None,
      metadata = Json.obj(),
      deletedAt = None
    )

    assertEquals(row.executorId, None)
    assertEquals(row.hostname, None)
    assertEquals(row.lastJobAt, None)
    assertEquals(row.version, None)
    assertEquals(row.deletedAt, None)
  }
