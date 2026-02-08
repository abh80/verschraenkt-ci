/*
 * VerschraenktCI Database Schema - Audit Tables
 * 
 * Creates the audit_log and schema_migrations tables.
 * The compliance folks love this stuff. Partitioned because it never shrinks.
 */

-- -----------------------------------------------------------------------------
-- Audit Log
-- The compliance folks love this stuff. Partitioned because it never shrinks.
-- -----------------------------------------------------------------------------

CREATE TABLE audit_log (
  audit_id BIGSERIAL,
  
  timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  audit_actor_type actor_type NOT NULL,  -- Using enum type
  actor_id VARCHAR(255) NOT NULL,
  
  action VARCHAR(100) NOT NULL,
  resource_type VARCHAR(50),
  resource_id VARCHAR(255),
  
  details JSONB DEFAULT '{}',
  
  ip_address INET,
  user_agent TEXT,
  
  success BOOLEAN NOT NULL,
  
  PRIMARY KEY (audit_id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Create partitions
CREATE TABLE audit_log_2026_01 PARTITION OF audit_log
  FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE audit_log_2026_02 PARTITION OF audit_log
  FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE audit_log_2026_03 PARTITION OF audit_log
  FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE audit_log_default PARTITION OF audit_log DEFAULT;

CREATE INDEX idx_audit_timestamp ON audit_log(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_log(actor_id, timestamp DESC);
CREATE INDEX idx_audit_resource ON audit_log(resource_type, resource_id, timestamp DESC);
CREATE INDEX idx_audit_action ON audit_log(action, timestamp DESC);
CREATE INDEX idx_audit_failed ON audit_log(timestamp DESC) WHERE success = false;

-- -----------------------------------------------------------------------------
-- Schema Migrations
-- -----------------------------------------------------------------------------

CREATE TABLE schema_migrations (
  version VARCHAR(50) PRIMARY KEY,
  description TEXT NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  checksum VARCHAR(64) NOT NULL CHECK (checksum ~ '^[a-fA-F0-9]{64}$'),
  execution_time_ms INT
);
