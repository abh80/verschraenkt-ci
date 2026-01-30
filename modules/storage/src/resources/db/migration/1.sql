/*
 * VerschraenktCI Database Schema v1
 * 
 * This is the main schema for our CI platform. 
 * We're aiming for something production-ready here.
 */

-- First off, let's define our enums to keep things consistent across tables.

CREATE TYPE execution_status AS ENUM (
  'pending', 
  'running', 
  'completed', 
  'failed', 
  'cancelled', 
  'timeout'
);

CREATE TYPE trigger_type AS ENUM (
  'manual',
  'webhook',
  'schedule',
  'api'
);

CREATE TYPE step_type AS ENUM (
  'checkout',
  'run',
  'cache_restore',
  'cache_save',
  'artifact_upload',
  'artifact_download',
  'composite'
);

CREATE TYPE secret_scope AS ENUM (
  'global',
  'pipeline',
  'workflow',
  'job'
);

CREATE TYPE storage_backend AS ENUM (
  's3',
  'minio',
  'gcs',
  'azure_blob'
);

CREATE TYPE executor_status AS ENUM (
  'online',
  'offline',
  'draining'
);

CREATE TYPE cache_status AS ENUM (
  'creating',
  'ready',
  'failed',
  'deleting'
);

CREATE TYPE cache_scope_type AS ENUM (
  'global',
  'branch',
  'pr',
  'repo',
  'commit'
);

CREATE TYPE actor_type AS ENUM (
  'user',
  'executor',
  'system',
  'api_token'
);

-- We need pgcrypto for the UUID generation below.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Custom UUID v7 generator. 
-- We want time-ordered UUIDs because they play much nicer with B-tree indexes than random v4s.
CREATE OR REPLACE FUNCTION uuid_generate_v7() RETURNS uuid AS $$
DECLARE
  unix_ts_ms BIGINT;
  uuid_bytes BYTEA;
BEGIN
  unix_ts_ms := (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT;
  uuid_bytes := decode(lpad(to_hex(unix_ts_ms), 12, '0'), 'hex') || gen_random_bytes(10);
  -- Set version (7) and variant bits
  uuid_bytes := set_byte(uuid_bytes, 6, (get_byte(uuid_bytes, 6) & 15) | 112);
  uuid_bytes := set_byte(uuid_bytes, 8, (get_byte(uuid_bytes, 8) & 63) | 128);
  RETURN encode(uuid_bytes, 'hex')::uuid;
END;
$$ LANGUAGE plpgsql VOLATILE;

-- -----------------------------------------------------------------------------
-- Core Tables
-- -----------------------------------------------------------------------------

-- The main pipelines table. We use soft-deletes here so we don't accidentally lose history.
CREATE TABLE pipelines (
  pipeline_id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(500) NOT NULL,
  definition JSONB NOT NULL,
  version INT NOT NULL DEFAULT 1,
  
  -- Timestamps
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  -- Audit fields (keep track of who did what)
  created_by VARCHAR(255) NOT NULL,
  
  labels TEXT[] DEFAULT '{}',
  is_active BOOLEAN NOT NULL DEFAULT true,
  
  -- Soft-delete support
  deleted_at TIMESTAMPTZ,
  deleted_by VARCHAR(255),
  
  -- Sanity check: make sure the definition has the required structure
  CONSTRAINT valid_definition CHECK (
    definition ? 'workflows' AND 
    jsonb_typeof(definition->'workflows') = 'array'
  ),
  
  CONSTRAINT unique_pipeline_version UNIQUE (pipeline_id, version)
);

CREATE INDEX idx_pipelines_labels ON pipelines USING GIN(labels);
CREATE INDEX idx_pipelines_created_at ON pipelines(created_at DESC);
CREATE INDEX idx_pipelines_active ON pipelines(is_active) WHERE is_active = true AND deleted_at IS NULL;
CREATE INDEX idx_pipelines_deleted ON pipelines(deleted_at) WHERE deleted_at IS NOT NULL;

-- Keep a history of pipeline versions so we can rollback if needed
CREATE TABLE pipeline_versions (
  pipeline_id VARCHAR(255) NOT NULL,
  version INT NOT NULL,
  definition JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by VARCHAR(255) NOT NULL,
  change_summary TEXT,
  
  PRIMARY KEY (pipeline_id, version),
  
  FOREIGN KEY (pipeline_id) REFERENCES pipelines(pipeline_id) ON DELETE CASCADE
);

CREATE INDEX idx_pipeline_versions_created ON pipeline_versions(pipeline_id, created_at DESC);

-- -----------------------------------------------------------------------------
-- Executors
-- Note: defined up here because likely referenced by job_executions.
-- -----------------------------------------------------------------------------   

CREATE TABLE executors (
  executor_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  
  name VARCHAR(255) NOT NULL,
  hostname VARCHAR(500),
  
  -- Capabilities
  platform VARCHAR(50) NOT NULL CHECK (platform IN ('linux', 'windows', 'macos')),
  architectures TEXT[] NOT NULL CHECK (array_length(architectures, 1) > 0),
  cpu_milli INT NOT NULL CHECK (cpu_milli > 0),
  memory_mib INT NOT NULL CHECK (memory_mib > 0),
  gpu INT DEFAULT 0 CHECK (gpu >= 0),
  disk_mib INT NOT NULL CHECK (disk_mib > 0),
  
  -- Labels for job matching
  labels JSONB DEFAULT '{}',
  
  -- State
  status executor_status NOT NULL DEFAULT 'online',
  
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_heartbeat TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_job_at TIMESTAMPTZ,
  
  -- Security first: token hash.
  -- We're enforcing reputable hashing algos (argon2, bcrypt, scrypt) via regex.
  token_hash VARCHAR(255) NOT NULL CHECK (
    token_hash ~ '^(\$argon2|\$2[aby]\$|\$scrypt\$)' OR 
    length(token_hash) = 64  -- SHA-256 hex fallback
  ),
  
  -- Metadata
  version VARCHAR(50),
  metadata JSONB DEFAULT '{}',
  
  -- Soft-delete
  deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_executor_name_unique ON executors(name) WHERE deleted_at IS NULL;

CREATE INDEX idx_executors_status ON executors(status, last_heartbeat DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_executors_platform ON executors(platform, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_executors_labels ON executors USING GIN(labels);
CREATE INDEX idx_executors_heartbeat ON executors(last_heartbeat DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_executors_stale ON executors(last_heartbeat) 
  WHERE status = 'online' AND deleted_at IS NULL;

-- -----------------------------------------------------------------------------
-- Executions
-- This tracks the actual runs of the pipelines.
-- -----------------------------------------------------------------------------

CREATE TABLE executions (
  execution_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  pipeline_id VARCHAR(255) NOT NULL,
  pipeline_version INT NOT NULL,
  status execution_status NOT NULL DEFAULT 'pending',
  
  -- Context about how this ran
  trigger trigger_type NOT NULL,
  trigger_by VARCHAR(255) NOT NULL,  -- We always need to blame someone ;)
  trigger_metadata JSONB,
  
  -- Prevent double-firing the same run
  idempotency_key VARCHAR(255) UNIQUE,
  
  -- Timing
  queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  timeout_at TIMESTAMPTZ,  -- The deadline. If we pass this, kill it.
  
  -- Concurrency control
  concurrency_group VARCHAR(255),
  concurrency_queue_position INT,
  
  -- Resource tracking
  total_cpu_milli_seconds BIGINT DEFAULT 0 CHECK (total_cpu_milli_seconds >= 0),
  total_memory_mib_seconds BIGINT DEFAULT 0 CHECK (total_memory_mib_seconds >= 0),
  
  -- Metadata
  labels JSONB DEFAULT '{}',
  error_message TEXT,
  
  -- Soft-delete
  deleted_at TIMESTAMPTZ,
  
  FOREIGN KEY (pipeline_id, pipeline_version) 
    REFERENCES pipelines(pipeline_id, version)
    ON DELETE RESTRICT,
    
  -- Ensure timing consistency
  CONSTRAINT valid_timing CHECK (
    (started_at IS NULL OR started_at >= queued_at) AND
    (completed_at IS NULL OR completed_at >= started_at)
  )
);

CREATE INDEX idx_executions_pipeline ON executions(pipeline_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_executions_status ON executions(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_executions_queued_at ON executions(queued_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_executions_concurrency ON executions(concurrency_group, status) 
  WHERE concurrency_group IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_executions_trigger ON executions(trigger, trigger_by) WHERE deleted_at IS NULL;
CREATE INDEX idx_executions_timeout ON executions(timeout_at) 
  WHERE status IN ('pending', 'running') AND timeout_at IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Workflow Executions
-- A pipeline is made of workflows.
-- -----------------------------------------------------------------------------

CREATE TABLE workflow_executions (
  workflow_execution_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  execution_id UUID NOT NULL,
  workflow_id VARCHAR(255) NOT NULL,
  workflow_name VARCHAR(500) NOT NULL,
  
  status execution_status NOT NULL DEFAULT 'pending',
  
  queued_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  timeout_at TIMESTAMPTZ,
  
  -- Condition evaluation
  condition_result BOOLEAN,
  condition_expression TEXT,
  
  error_message TEXT,
  
  FOREIGN KEY (execution_id) REFERENCES executions(execution_id) 
    ON DELETE CASCADE,
    
  CONSTRAINT valid_workflow_timing CHECK (
    (started_at IS NULL OR started_at >= queued_at) AND
    (completed_at IS NULL OR completed_at >= started_at)
  )
);

CREATE INDEX idx_workflow_executions_execution ON workflow_executions(execution_id);
CREATE INDEX idx_workflow_executions_status ON workflow_executions(status);
CREATE INDEX idx_workflow_executions_started ON workflow_executions(started_at DESC);

-- -----------------------------------------------------------------------------
-- Job Executions
-- Workflows are made of jobs. This is the meat of the execution.
-- -----------------------------------------------------------------------------

CREATE TABLE job_executions (
  job_execution_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  workflow_execution_id UUID NOT NULL,
  execution_id UUID NOT NULL,  -- Denormalized so we don't have to join 5 tables to find the parent
  
  job_id VARCHAR(255) NOT NULL,
  job_name VARCHAR(500) NOT NULL,
  
  status execution_status NOT NULL DEFAULT 'pending',
  
  -- Scheduling
  dependencies JSONB DEFAULT '[]',
  ready_at TIMESTAMPTZ,
  scheduled_at TIMESTAMPTZ,
  
  -- Execution
  executor_id UUID,
  assigned_at TIMESTAMPTZ,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  timeout_at TIMESTAMPTZ,
  
  -- Resources with validation
  requested_cpu_milli INT CHECK (requested_cpu_milli IS NULL OR requested_cpu_milli > 0),
  requested_memory_mib INT CHECK (requested_memory_mib IS NULL OR requested_memory_mib > 0),
  requested_gpu INT CHECK (requested_gpu IS NULL OR requested_gpu >= 0),
  requested_disk_mib INT CHECK (requested_disk_mib IS NULL OR requested_disk_mib > 0),
  
  actual_cpu_milli_seconds BIGINT CHECK (actual_cpu_milli_seconds IS NULL OR actual_cpu_milli_seconds >= 0),
  actual_memory_mib_seconds BIGINT CHECK (actual_memory_mib_seconds IS NULL OR actual_memory_mib_seconds >= 0),
  
  -- Retry logic
  attempt_number INT NOT NULL DEFAULT 1 CHECK (attempt_number >= 1),
  max_attempts INT NOT NULL DEFAULT 1 CHECK (max_attempts >= 1),
  
  -- Results
  exit_code INT,
  error_message TEXT,
  
  -- Matrix jobs
  matrix_coordinates JSONB,
  
  FOREIGN KEY (workflow_execution_id) 
    REFERENCES workflow_executions(workflow_execution_id) 
    ON DELETE CASCADE,
  FOREIGN KEY (executor_id) 
    REFERENCES executors(executor_id) 
    ON DELETE SET NULL,
    
  CONSTRAINT valid_job_timing CHECK (
    (started_at IS NULL OR started_at >= ready_at OR ready_at IS NULL) AND
    (completed_at IS NULL OR completed_at >= started_at)
  ),
  CONSTRAINT valid_retry_config CHECK (attempt_number <= max_attempts)
);

CREATE INDEX idx_job_executions_workflow ON job_executions(workflow_execution_id);
CREATE INDEX idx_job_executions_execution ON job_executions(execution_id);
CREATE INDEX idx_job_executions_status ON job_executions(status);
CREATE INDEX idx_job_executions_executor ON job_executions(executor_id) WHERE executor_id IS NOT NULL;
CREATE INDEX idx_job_executions_ready ON job_executions(ready_at) WHERE status = 'pending';
CREATE INDEX idx_job_executions_matrix ON job_executions USING GIN(matrix_coordinates) 
  WHERE matrix_coordinates IS NOT NULL;
CREATE INDEX idx_job_executions_pending_full ON job_executions(ready_at)
  INCLUDE (workflow_execution_id, job_id, requested_cpu_milli, requested_memory_mib)
  WHERE status = 'pending';
CREATE INDEX idx_job_executions_timeout ON job_executions(timeout_at) 
  WHERE status IN ('pending', 'running') AND timeout_at IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Step Executions
-- Jobs are made of steps. The atomic units of work.
-- -----------------------------------------------------------------------------

CREATE TABLE step_executions (
  step_execution_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  job_execution_id UUID NOT NULL,
  
  step_id VARCHAR(255) NOT NULL,
  step_kind step_type NOT NULL,  -- Using enum type
  step_index INT NOT NULL CHECK (step_index >= 0),
  
  status execution_status NOT NULL DEFAULT 'pending',
  
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  
  -- Command details (for Run steps)
  command_text TEXT,
  shell VARCHAR(50),
  
  -- Results
  exit_code INT,
  stdout_location TEXT,
  stderr_location TEXT,
  
  error_message TEXT,
  continue_on_error BOOLEAN DEFAULT false,
  
  FOREIGN KEY (job_execution_id) 
    REFERENCES job_executions(job_execution_id) 
    ON DELETE CASCADE,
    
  CONSTRAINT unique_step_order UNIQUE (job_execution_id, step_index),
  CONSTRAINT valid_step_timing CHECK (
    completed_at IS NULL OR started_at IS NULL OR completed_at >= started_at
  )
);

CREATE INDEX idx_step_executions_job ON step_executions(job_execution_id, step_index);
CREATE INDEX idx_step_executions_status ON step_executions(status);

-- -----------------------------------------------------------------------------
-- Execution Events
-- These grow fast, so we partition by month.
-- -----------------------------------------------------------------------------

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

-- -----------------------------------------------------------------------------
-- Executor Assignments
-- Tracking who is doing what.
-- -----------------------------------------------------------------------------

CREATE TABLE executor_assignments (
  assignment_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  
  job_execution_id UUID NOT NULL UNIQUE,
  executor_id UUID NOT NULL,
  
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  
  FOREIGN KEY (job_execution_id) 
    REFERENCES job_executions(job_execution_id) 
    ON DELETE CASCADE,
  FOREIGN KEY (executor_id) 
    REFERENCES executors(executor_id) 
    ON DELETE CASCADE
);

CREATE INDEX idx_assignments_executor ON executor_assignments(executor_id, assigned_at DESC);
CREATE INDEX idx_assignments_job ON executor_assignments(job_execution_id);

-- -----------------------------------------------------------------------------
-- Artifacts
-- Files produced by the build.
-- -----------------------------------------------------------------------------

CREATE TABLE artifacts (
  artifact_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  
  job_execution_id UUID NOT NULL,
  execution_id UUID NOT NULL,
  
  name VARCHAR(500) NOT NULL,
  path VARCHAR(1000) NOT NULL,
  
  -- Where are we storing this blob?
  storage_backend storage_backend NOT NULL DEFAULT 's3',
  storage_key TEXT NOT NULL,
  storage_bucket VARCHAR(255) NOT NULL,
  
  -- Metadata
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
  content_type VARCHAR(255),
  checksum_sha256 VARCHAR(64) CHECK (checksum_sha256 ~ '^[a-fA-F0-9]{64}$'),
  
  uploaded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ,
  
  -- Access control with audit
  is_public BOOLEAN DEFAULT false,
  made_public_at TIMESTAMPTZ,
  made_public_by VARCHAR(255),
  
  FOREIGN KEY (job_execution_id) 
    REFERENCES job_executions(job_execution_id) 
    ON DELETE CASCADE,
    
  CONSTRAINT public_audit CHECK (
    (is_public = false) OR 
    (is_public = true AND made_public_at IS NOT NULL AND made_public_by IS NOT NULL)
  )
);

CREATE INDEX idx_artifacts_job ON artifacts(job_execution_id);
CREATE INDEX idx_artifacts_execution ON artifacts(execution_id, uploaded_at DESC);
CREATE INDEX idx_artifacts_expires ON artifacts(expires_at) 
  WHERE expires_at IS NOT NULL;
CREATE INDEX idx_artifacts_public ON artifacts(is_public, uploaded_at DESC) 
  WHERE is_public = true;

-- -----------------------------------------------------------------------------
-- Cache Entries
-- Speed up builds by saving state.
-- -----------------------------------------------------------------------------

CREATE TABLE cache_entries (
  cache_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),

  -- What is this cache actually for?
  cache_key VARCHAR(500) NOT NULL,
  cache_version VARCHAR(100) NOT NULL DEFAULT 'v1',

  -- Who can see this?
  scope_type cache_scope_type NOT NULL,
  scope_value VARCHAR(500),

  -- Fingerprints to know if it changed
  paths_hash VARCHAR(64) NOT NULL CHECK (paths_hash ~ '^[a-fA-F0-9]{64}$'),
  input_hash VARCHAR(64) NOT NULL CHECK (input_hash ~ '^[a-fA-F0-9]{64}$'),
  content_hash VARCHAR(64) CHECK (content_hash ~ '^[a-fA-F0-9]{64}$'),

  -- Backend details
  storage_provider storage_backend NOT NULL DEFAULT 's3',
  storage_bucket VARCHAR(255) NOT NULL,
  storage_key TEXT NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),

  -- Is it ready yet?
  status cache_status NOT NULL DEFAULT 'creating',

  created_by_execution UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  last_accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  access_count INT NOT NULL DEFAULT 0 CHECK (access_count >= 0),

  expires_at TIMESTAMPTZ,

  FOREIGN KEY (created_by_execution)
    REFERENCES executions(execution_id)
    ON DELETE SET NULL,

  CONSTRAINT unique_cache_identity UNIQUE (
    cache_key,
    cache_version,
    scope_type,
    scope_value,
    paths_hash,
    input_hash
  )
);

-- Quick lookup for cache hits
CREATE INDEX idx_cache_key_scope ON cache_entries(cache_key, scope_type, scope_value);
CREATE INDEX idx_cache_accessed ON cache_entries(last_accessed_at DESC);

-- Active cache only
CREATE INDEX idx_cache_active ON cache_entries(cache_key, scope_type, scope_value)
  WHERE status = 'ready';

-- Helper for the garbage collector
CREATE INDEX idx_cache_eviction ON cache_entries(last_accessed_at ASC);

-- Expiry cleanup (single definition)
CREATE INDEX idx_cache_expires ON cache_entries(expires_at) 
  WHERE expires_at IS NOT NULL;

-- Covering index so we don't have to touch the heap if it's a hit
CREATE INDEX idx_cache_lookup ON cache_entries(cache_key, scope_type, scope_value, paths_hash, input_hash)
  INCLUDE (storage_key, storage_bucket, status, expires_at);

-- -----------------------------------------------------------------------------
-- Secrets
-- Shhh.
-- -----------------------------------------------------------------------------

CREATE TABLE secrets (
  secret_id UUID PRIMARY KEY DEFAULT uuid_generate_v7(),
  
  name VARCHAR(255) NOT NULL,
  scope secret_scope NOT NULL,  -- Using enum type
  scope_entity_id VARCHAR(255),
  
  -- Where is it really stored?
  vault_path_encrypted BYTEA NOT NULL,  -- Encrypted with KMS so even DB admins can't peek easily
  vault_path_key_id VARCHAR(255) NOT NULL,  -- Which key opens this lock?
  version INT NOT NULL DEFAULT 1 CHECK (version >= 1),
  
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  rotated_at TIMESTAMPTZ,  -- Track key rotation
  created_by VARCHAR(255) NOT NULL,  -- NOT NULL for accountability
  
  is_active BOOLEAN DEFAULT true,
  
  -- Soft-delete
  deleted_at TIMESTAMPTZ,
  deleted_by VARCHAR(255)
);

CREATE UNIQUE INDEX idx_secrets_name_scope_unique ON secrets(name, scope, scope_entity_id) WHERE deleted_at IS NULL;

CREATE INDEX idx_secrets_scope ON secrets(scope, scope_entity_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_secrets_name ON secrets(name) WHERE deleted_at IS NULL;
CREATE INDEX idx_secrets_rotation ON secrets(rotated_at) WHERE is_active = true;

-- -----------------------------------------------------------------------------
-- Secret Access Log
-- Who peeked?
-- -----------------------------------------------------------------------------

CREATE TABLE secret_access_log (
  log_id BIGSERIAL PRIMARY KEY,
  
  secret_id UUID NOT NULL,
  job_execution_id UUID NOT NULL,
  
  accessed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  executor_id UUID NOT NULL,  -- NOT NULL: always know which executor accessed
  
  granted BOOLEAN NOT NULL,
  denial_reason TEXT,
  
  -- Additional audit context
  request_ip INET,
  
  FOREIGN KEY (secret_id) REFERENCES secrets(secret_id) ON DELETE CASCADE,
  FOREIGN KEY (job_execution_id) 
    REFERENCES job_executions(job_execution_id) 
    ON DELETE CASCADE,
  FOREIGN KEY (executor_id)
    REFERENCES executors(executor_id)
    ON DELETE SET NULL
);

CREATE INDEX idx_secret_access_log_secret ON secret_access_log(secret_id, accessed_at DESC);
CREATE INDEX idx_secret_access_log_job ON secret_access_log(job_execution_id);
CREATE INDEX idx_secret_access_log_denied ON secret_access_log(accessed_at DESC) 
  WHERE granted = false;

-- -----------------------------------------------------------------------------
-- Metrics Snapshots
-- High volume data, partitioned to keep queries reasonable.
-- -----------------------------------------------------------------------------

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

-- -----------------------------------------------------------------------------
-- Row-Level Security Policies
-- Let's lock this down a bit.
-- -----------------------------------------------------------------------------

-- Enable RLS on sensitive tables
ALTER TABLE pipelines ENABLE ROW LEVEL SECURITY;
ALTER TABLE executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE secrets ENABLE ROW LEVEL SECURITY;
ALTER TABLE secret_access_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;

-- Pipelines: users see their own stuff, admins see everything
CREATE POLICY pipeline_owner_policy ON pipelines
  FOR ALL
  USING (
    created_by = current_setting('app.current_user', true) OR
    current_setting('app.is_admin', true)::boolean = true
  );

-- Executions: same deal, you run it, you see it
CREATE POLICY execution_owner_policy ON executions
  FOR ALL
  USING (
    trigger_by = current_setting('app.current_user', true) OR
    current_setting('app.is_admin', true)::boolean = true
  );

-- Secrets: highly sensitive, strict need-to-know basis
CREATE POLICY secret_access_policy ON secrets
  FOR ALL
  USING (
    current_setting('app.is_admin', true)::boolean = true OR
    (scope = 'global' AND current_setting('app.can_read_global_secrets', true)::boolean = true) OR
    (scope != 'global' AND created_by = current_setting('app.current_user', true))
  );

-- Secret access log: admin eyes only
CREATE POLICY secret_log_admin_policy ON secret_access_log
  FOR SELECT
  USING (current_setting('app.is_admin', true)::boolean = true);

-- Audit log: admin eyes only
CREATE POLICY audit_admin_policy ON audit_log
  FOR SELECT
  USING (current_setting('app.is_admin', true)::boolean = true);

-- -----------------------------------------------------------------------------
-- Helper Functions
-- -----------------------------------------------------------------------------

-- Trigger function to keep our 'updated_at' columns fresh
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Wiring up the triggers
CREATE TRIGGER update_pipelines_updated_at
  BEFORE UPDATE ON pipelines
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_secrets_updated_at
  BEFORE UPDATE ON secrets
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_cache_entries_updated_at
  BEFORE UPDATE ON cache_entries
  FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Auto-create those monthly partitions so we don't have to wake up at midnight on the 1st
CREATE OR REPLACE FUNCTION create_monthly_partition(
  parent_table TEXT,
  partition_date DATE
) RETURNS VOID AS $$
DECLARE
  partition_name TEXT;
  start_date DATE;
  end_date DATE;
BEGIN
  start_date := date_trunc('month', partition_date);
  end_date := start_date + INTERVAL '1 month';
  partition_name := parent_table || '_' || to_char(start_date, 'YYYY_MM');
  
  EXECUTE format(
    'CREATE TABLE IF NOT EXISTS %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
    partition_name, parent_table, start_date, end_date
  );
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- Documentation Comments
-- These show up in DB tools, which is nice.
-- -----------------------------------------------------------------------------

COMMENT ON TABLE pipelines IS 'CI/CD pipeline definitions with version history';
COMMENT ON TABLE executions IS 'Pipeline execution instances';
COMMENT ON TABLE executors IS 'Registered execution agents/runners';
COMMENT ON TABLE secrets IS 'Secret references stored in external vault (encrypted paths)';
COMMENT ON TABLE cache_entries IS 'Build cache entries with scope-based isolation';
COMMENT ON TABLE audit_log IS 'Immutable audit trail for compliance';

COMMENT ON COLUMN secrets.vault_path_encrypted IS 'AES-256 encrypted vault path, decrypted at runtime';
COMMENT ON COLUMN secrets.vault_path_key_id IS 'KMS key ID for rotation tracking';
COMMENT ON COLUMN executors.token_hash IS 'Argon2/bcrypt hashed authentication token';
