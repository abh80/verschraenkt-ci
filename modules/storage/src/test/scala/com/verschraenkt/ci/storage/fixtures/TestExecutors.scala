package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.storage.db.codecs.Enums.{ Architecture, ExecutorStatus, Platform }
import com.verschraenkt.ci.storage.db.tables.ExecutorRow
import io.circe.Json

import java.time.Instant
import java.util.UUID

/** Reusable test data for executor tests */
object TestExecutors:

  def onlineExecutor(name: String = "executor-1"): ExecutorRow =
    ExecutorRow(
      executorId = Some(UUID.randomUUID()),
      name = name,
      hostname = Some("worker-01.example.com"),
      platform = Platform.Linux,
      architecture = Architecture.X86_64,
      cpuMillis = 8000,
      memoryMibs = 16384,
      gpuCount = 0,
      diskMibs = 102400,
      labels = List("linux", "x86_64", "general"),
      status = ExecutorStatus.Online,
      registeredAt = Instant.now().minusSeconds(86400),
      lastHeartbeat = Instant.now().minusSeconds(10),
      lastJobAt = Some(Instant.now().minusSeconds(300)),
      tokenHash = "sha256:abc123def456",
      version = Some("1.0.0"),
      metadata = Json.obj("region" -> Json.fromString("us-east-1")),
      deletedAt = None
    )

  def offlineExecutor(name: String = "executor-2"): ExecutorRow =
    ExecutorRow(
      executorId = Some(UUID.randomUUID()),
      name = name,
      hostname = Some("worker-02.example.com"),
      platform = Platform.Linux,
      architecture = Architecture.Arm64,
      cpuMillis = 4000,
      memoryMibs = 8192,
      gpuCount = 0,
      diskMibs = 51200,
      labels = List("linux", "arm64"),
      status = ExecutorStatus.Offline,
      registeredAt = Instant.now().minusSeconds(172800),
      lastHeartbeat = Instant.now().minusSeconds(3600),
      lastJobAt = Some(Instant.now().minusSeconds(7200)),
      tokenHash = "sha256:ghi789jkl012",
      version = Some("0.9.0"),
      metadata = Json.obj(),
      deletedAt = None
    )

  def drainingExecutor(name: String = "executor-3"): ExecutorRow =
    ExecutorRow(
      executorId = Some(UUID.randomUUID()),
      name = name,
      hostname = Some("worker-03.example.com"),
      platform = Platform.Linux,
      architecture = Architecture.X86_64,
      cpuMillis = 16000,
      memoryMibs = 32768,
      gpuCount = 2,
      diskMibs = 204800,
      labels = List("linux", "x86_64", "gpu", "high-memory"),
      status = ExecutorStatus.Draining,
      registeredAt = Instant.now().minusSeconds(604800),
      lastHeartbeat = Instant.now().minusSeconds(5),
      lastJobAt = Some(Instant.now().minusSeconds(60)),
      tokenHash = "sha256:mno345pqr678",
      version = Some("1.0.0"),
      metadata = Json.obj(
        "region"   -> Json.fromString("eu-west-1"),
        "gpu_type" -> Json.fromString("nvidia-t4")
      ),
      deletedAt = None
    )

  def newExecutor(name: String = "executor-new"): ExecutorRow =
    ExecutorRow(
      executorId = None,
      name = name,
      hostname = None,
      platform = Platform.Windows64b,
      architecture = Architecture.X86_64,
      cpuMillis = 4000,
      memoryMibs = 8192,
      gpuCount = 0,
      diskMibs = 51200,
      labels = List("windows"),
      status = ExecutorStatus.Online,
      registeredAt = Instant.now(),
      lastHeartbeat = Instant.now(),
      lastJobAt = None,
      tokenHash = "sha256:stu901vwx234",
      version = None,
      metadata = Json.obj(),
      deletedAt = None
    )

  def deletedExecutor(name: String = "executor-deleted"): ExecutorRow =
    ExecutorRow(
      executorId = Some(UUID.randomUUID()),
      name = name,
      hostname = Some("worker-deleted.example.com"),
      platform = Platform.MacOS,
      architecture = Architecture.Arm64,
      cpuMillis = 8000,
      memoryMibs = 16384,
      gpuCount = 0,
      diskMibs = 256000,
      labels = List("macos", "arm64"),
      status = ExecutorStatus.Offline,
      registeredAt = Instant.now().minusSeconds(604800),
      lastHeartbeat = Instant.now().minusSeconds(86400),
      lastJobAt = Some(Instant.now().minusSeconds(172800)),
      tokenHash = "sha256:yz0123abc456",
      version = Some("0.8.0"),
      metadata = Json.obj(),
      deletedAt = Some(Instant.now().minusSeconds(3600))
    )

  def withStatus(status: ExecutorStatus): ExecutorRow =
    onlineExecutor().copy(status = status)
