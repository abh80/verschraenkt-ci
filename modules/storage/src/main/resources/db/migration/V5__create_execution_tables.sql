/*
 * VerschraenktCI Database Schema - Execution Tables
 * 
 * Creates the main execution tracking tables:
 * - executions (pipeline runs)
 * - workflow_executions (workflows within pipeline runs)
 * - job_executions (jobs within workflows)
 * - step_executions (steps within jobs)
 */

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
    REFERENCES pipeline_versions(pipeline_id, version)
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
