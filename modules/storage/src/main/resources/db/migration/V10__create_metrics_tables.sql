/*
 * VerschraenktCI Database Schema - Metrics Tables
 * 
 * Creates the metrics_snapshots table with time-based partitioning.
 * High volume data, partitioned to keep queries reasonable.
 */

CREATE TABLE metrics_snapshots (
  snapshot_id BIGSERIAL,
  
  job_execution_id UUID NOT NULL,
  
  timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  -- Resource usage with validation
  cpu_usage_milli INT CHECK (cpu_usage_milli IS NULL OR cpu_usage_milli >= 0),
  memory_usage_mib INT CHECK (memory_usage_mib IS NULL OR memory_usage_mib >= 0),
  disk_usage_mib INT CHECK (disk_usage_mib IS NULL OR disk_usage_mib >= 0),
  network_rx_bytes BIGINT CHECK (network_rx_bytes IS NULL OR network_rx_bytes >= 0),
  network_tx_bytes BIGINT CHECK (network_tx_bytes IS NULL OR network_tx_bytes >= 0),
  
  PRIMARY KEY (snapshot_id, timestamp),
  
  FOREIGN KEY (job_execution_id) 
    REFERENCES job_executions(job_execution_id) 
    ON DELETE CASCADE
) PARTITION BY RANGE (timestamp);

-- Create partitions
CREATE TABLE metrics_snapshots_2026_01 PARTITION OF metrics_snapshots
  FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE metrics_snapshots_2026_02 PARTITION OF metrics_snapshots
  FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE metrics_snapshots_2026_03 PARTITION OF metrics_snapshots
  FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE metrics_snapshots_default PARTITION OF metrics_snapshots DEFAULT;

CREATE INDEX idx_metrics_job_time ON metrics_snapshots(job_execution_id, timestamp DESC);
-- BRIN index for time-series efficiency
CREATE INDEX idx_metrics_time_brin ON metrics_snapshots USING BRIN(timestamp) 
  WITH (pages_per_range = 128);
