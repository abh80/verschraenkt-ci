package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import slick.model.ForeignKeyAction.Cascade

import java.time.Instant

/** Database row representation of a metrics snapshot capturing resource usage at a point in time.
  *
  * @param snapshotId
  *   Auto-generated sequential ID (part of composite PK with timestamp)
  * @param jobExecutionId
  *   Job execution this snapshot belongs to
  * @param timestamp
  *   When the snapshot was taken (partition key)
  * @param cpuUsageMilli
  *   CPU usage in milli-cores at snapshot time
  * @param memoryUsageMib
  *   Memory usage in MiB at snapshot time
  * @param diskUsageMib
  *   Disk usage in MiB at snapshot time
  * @param networkRxBytes
  *   Network bytes received since last snapshot
  * @param networkTxBytes
  *   Network bytes transmitted since last snapshot
  */
case class MetricsSnapshotRow(
    snapshotId: Option[Long],
    jobExecutionId: Long,
    timestamp: Instant,
    cpuUsageMilli: Option[Int],
    memoryUsageMib: Option[Int],
    diskUsageMib: Option[Int],
    networkRxBytes: Option[Long],
    networkTxBytes: Option[Long]
)

class MetricsSnapshotTable(tag: Tag) extends Table[MetricsSnapshotRow](tag, "metrics_snapshots"):
  def snapshotId     = column[Long]("snapshot_id", O.AutoInc)
  def jobExecutionId = column[Long]("job_execution_id")
  def timestamp      = column[Instant]("timestamp")
  def cpuUsageMilli  = column[Option[Int]]("cpu_usage_milli")
  def memoryUsageMib = column[Option[Int]]("memory_usage_mib")
  def diskUsageMib   = column[Option[Int]]("disk_usage_mib")
  def networkRxBytes = column[Option[Long]]("network_rx_bytes")
  def networkTxBytes = column[Option[Long]]("network_tx_bytes")

  def * = (
    snapshotId.?,
    jobExecutionId,
    timestamp,
    cpuUsageMilli,
    memoryUsageMib,
    diskUsageMib,
    networkRxBytes,
    networkTxBytes
  ) <> (MetricsSnapshotRow.apply.tupled, MetricsSnapshotRow.unapply)

  def pk = primaryKey("metrics_snapshots_pkey", (snapshotId, timestamp))

  def fk_jobExecutionId = foreignKey(
    "metrics_snapshots_job_execution_id_fkey",
    jobExecutionId,
    TableQuery[JobExecutionTable]
  )(_.jobExecutionId, onDelete = Cascade)
