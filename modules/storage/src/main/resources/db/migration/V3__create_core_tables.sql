/*
 * VerschraenktCI Database Schema - Core Tables
 * 
 * Creates the pipeline and pipeline_versions tables.
 * These are the foundation of the CI/CD platform.
 */

-- The main pipelines table. We use soft-deletes here so we don't accidentally lose history.
CREATE TABLE pipelines (
  pipeline_id VARCHAR(255) PRIMARY KEY,
  name VARCHAR(500) NOT NULL,
  current_version INT NOT NULL DEFAULT 1,
  definition JSONB NOT NULL,
  
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
  )
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
