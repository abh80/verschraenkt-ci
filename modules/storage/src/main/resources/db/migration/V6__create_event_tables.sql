/*
 * VerschraenktCI Database Schema - Event Tables
 * 
 * Creates the execution_events table with time-based partitioning.
 * These tables grow fast, so we partition by month for manageability.
 */

CREATE TABLE execution_events (
  event_id BIGSERIAL,
  event_uuid UUID NOT NULL DEFAULT uuid_generate_v7(),
  
  execution_id UUID NOT NULL,
  workflow_execution_id UUID,
  job_execution_id UUID,
  step_execution_id UUID,
  
  event_type VARCHAR(100) NOT NULL,
  event_payload JSONB NOT NULL,
  
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  correlation_id UUID,
  
  PRIMARY KEY (event_id, occurred_at),
  
  FOREIGN KEY (execution_id) REFERENCES executions(execution_id) 
    ON DELETE CASCADE
) PARTITION BY RANGE (occurred_at);

-- Create partitions for upcoming months
CREATE TABLE execution_events_2026_01 PARTITION OF execution_events
  FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE execution_events_2026_02 PARTITION OF execution_events
  FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE execution_events_2026_03 PARTITION OF execution_events
  FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE execution_events_default PARTITION OF execution_events DEFAULT;

CREATE INDEX idx_events_execution ON execution_events(execution_id, event_id);
CREATE INDEX idx_events_job ON execution_events(job_execution_id, event_id) 
  WHERE job_execution_id IS NOT NULL;
CREATE INDEX idx_events_type ON execution_events(event_type, occurred_at DESC);
CREATE INDEX idx_events_occurred ON execution_events(occurred_at DESC);
CREATE INDEX idx_events_correlation ON execution_events(correlation_id) 
  WHERE correlation_id IS NOT NULL;
