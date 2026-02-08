/*
 * VerschraenktCI Database Schema - Assignment and Artifact Tables
 * 
 * Creates tables for tracking executor assignments and build artifacts.
 */

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
