package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.fixtures.TestMetricsSnapshots
import munit.FunSuite

import java.time.Instant

class MetricsSnapshotTableSpec extends FunSuite:

  test("MetricsSnapshotRow creates correct row with all fields") {
    val row = TestMetricsSnapshots.snapshot()

    assertEquals(row.snapshotId, None)
    assert(row.jobExecutionId > 0L)
    assert(row.cpuUsageMilli.contains(500))
    assert(row.memoryUsageMib.contains(1024))
    assert(row.diskUsageMib.contains(5120))
    assert(row.networkRxBytes.contains(1048576L))
    assert(row.networkTxBytes.contains(524288L))
  }

  test("MetricsSnapshotRow handles cpu-only snapshot") {
    val row = TestMetricsSnapshots.cpuOnlySnapshot(cpuUsageMilli = 2000)

    assert(row.cpuUsageMilli.contains(2000))
    assertEquals(row.memoryUsageMib, None)
    assertEquals(row.diskUsageMib, None)
    assertEquals(row.networkRxBytes, None)
    assertEquals(row.networkTxBytes, None)
  }

  test("MetricsSnapshotRow handles high usage snapshot") {
    val row = TestMetricsSnapshots.highUsageSnapshot()

    assert(row.cpuUsageMilli.contains(8000))
    assert(row.memoryUsageMib.contains(16384))
    assert(row.diskUsageMib.contains(102400))
    assert(row.networkRxBytes.contains(10485760L))
    assert(row.networkTxBytes.contains(5242880L))
  }

  test("MetricsSnapshotRow handles empty snapshot") {
    val row = TestMetricsSnapshots.emptySnapshot()

    assertEquals(row.snapshotId, None)
    assertEquals(row.cpuUsageMilli, None)
    assertEquals(row.memoryUsageMib, None)
    assertEquals(row.diskUsageMib, None)
    assertEquals(row.networkRxBytes, None)
    assertEquals(row.networkTxBytes, None)
  }

  test("MetricsSnapshotRow stores job execution reference") {
    val row = TestMetricsSnapshots.snapshot(jobExecutionId = 42L)

    assertEquals(row.jobExecutionId, 42L)
  }

  test("MetricsSnapshotRow stores custom timestamp") {
    val ts  = Instant.parse("2026-01-15T10:30:00Z")
    val row = TestMetricsSnapshots.snapshot(timestamp = ts)

    assertEquals(row.timestamp, ts)
  }

  test("MetricsSnapshotRow stores custom resource values") {
    val row = TestMetricsSnapshots.snapshot(
      cpuUsageMilli = Some(4000),
      memoryUsageMib = Some(8192),
      diskUsageMib = Some(51200),
      networkRxBytes = Some(2097152L),
      networkTxBytes = Some(1048576L)
    )

    assert(row.cpuUsageMilli.contains(4000))
    assert(row.memoryUsageMib.contains(8192))
    assert(row.diskUsageMib.contains(51200))
    assert(row.networkRxBytes.contains(2097152L))
    assert(row.networkTxBytes.contains(1048576L))
  }

  test("MetricsSnapshotRow default values for generated fields") {
    val row = MetricsSnapshotRow(
      snapshotId = None,
      jobExecutionId = 1L,
      timestamp = Instant.now(),
      cpuUsageMilli = None,
      memoryUsageMib = None,
      diskUsageMib = None,
      networkRxBytes = None,
      networkTxBytes = None
    )

    assertEquals(row.snapshotId, None)
    assertEquals(row.cpuUsageMilli, None)
    assertEquals(row.memoryUsageMib, None)
    assertEquals(row.diskUsageMib, None)
    assertEquals(row.networkRxBytes, None)
    assertEquals(row.networkTxBytes, None)
  }
