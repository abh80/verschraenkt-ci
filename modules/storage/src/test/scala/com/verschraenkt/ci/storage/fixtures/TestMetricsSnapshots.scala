package com.verschraenkt.ci.storage.fixtures

import com.verschraenkt.ci.engine.api.SnowflakeProvider
import com.verschraenkt.ci.storage.db.tables.MetricsSnapshotRow

import java.time.Instant

/** Reusable test data for metrics snapshot tests */
object TestMetricsSnapshots:

  private val snowflakeProvider = SnowflakeProvider.make(76)
  private def nextId            = snowflakeProvider.nextId().value

  def snapshot(
      jobExecutionId: Long = nextId,
      timestamp: Instant = Instant.now(),
      cpuUsageMilli: Option[Int] = Some(500),
      memoryUsageMib: Option[Int] = Some(1024),
      diskUsageMib: Option[Int] = Some(5120),
      networkRxBytes: Option[Long] = Some(1048576L),
      networkTxBytes: Option[Long] = Some(524288L)
  ): MetricsSnapshotRow =
    MetricsSnapshotRow(
      snapshotId = None,
      jobExecutionId = jobExecutionId,
      timestamp = timestamp,
      cpuUsageMilli = cpuUsageMilli,
      memoryUsageMib = memoryUsageMib,
      diskUsageMib = diskUsageMib,
      networkRxBytes = networkRxBytes,
      networkTxBytes = networkTxBytes
    )

  def cpuOnlySnapshot(
      jobExecutionId: Long = nextId,
      cpuUsageMilli: Int = 2000
  ): MetricsSnapshotRow =
    MetricsSnapshotRow(
      snapshotId = None,
      jobExecutionId = jobExecutionId,
      timestamp = Instant.now(),
      cpuUsageMilli = Some(cpuUsageMilli),
      memoryUsageMib = None,
      diskUsageMib = None,
      networkRxBytes = None,
      networkTxBytes = None
    )

  def highUsageSnapshot(
      jobExecutionId: Long = nextId
  ): MetricsSnapshotRow =
    MetricsSnapshotRow(
      snapshotId = None,
      jobExecutionId = jobExecutionId,
      timestamp = Instant.now(),
      cpuUsageMilli = Some(8000),
      memoryUsageMib = Some(16384),
      diskUsageMib = Some(102400),
      networkRxBytes = Some(10485760L),
      networkTxBytes = Some(5242880L)
    )

  def emptySnapshot(
      jobExecutionId: Long = nextId
  ): MetricsSnapshotRow =
    MetricsSnapshotRow(
      snapshotId = None,
      jobExecutionId = jobExecutionId,
      timestamp = Instant.now(),
      cpuUsageMilli = None,
      memoryUsageMib = None,
      diskUsageMib = None,
      networkRxBytes = None,
      networkTxBytes = None
    )
