/*
 * VerschraenktCI Database Schema - Executor Tables
 * 
 * Creates the executors table for tracking build agents/runners.
 * Defined early because it's referenced by job_executions.
 */

CREATE TABLE executors (
  executor_id UUID PRIMARY KEY,
  
  name VARCHAR(255) NOT NULL,
  hostname VARCHAR(500),
  
  -- Capabilities
  platform platform_type NOT NULL,
  architectures architecture_type NOT NULL,
  cpu_milli INT NOT NULL CHECK (cpu_milli > 0),
  memory_mib INT NOT NULL CHECK (memory_mib > 0),
  gpu INT DEFAULT 0 CHECK (gpu >= 0),
  disk_mib INT NOT NULL CHECK (disk_mib > 0),
  
  -- Labels for job matching
  labels TEXT[] DEFAULT '{}',
  
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
  /*
    Metadata is used to store executor related preferences:
  - Runtime info: container runtime (docker/podman), supported image registries, installed
  toolchains/SDKs
  - Network details: IP address, region/zone, proxy config, network tags
  - Resource details: specific GPU model, storage type (SSD/HDD), available disk partitions
  - Custom config: max concurrent jobs, timeout overrides, environment variables, feature
  flags
  - Provider info: cloud provider, instance type, auto-scaling group, spot/preemptible flag
   */
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
