/*
 * VerschraenktCI Database Schema - Secret Tables
 * 
 * Creates tables for secret management and access logging.
 * Secrets are stored encrypted with audit trails.
 */

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
