/*
 * VerschraenktCI Database Schema - Cache Tables
 * 
 * Creates the cache_entries table for build cache management.
 * Caching speeds up builds by saving state across runs.
 */

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

-- Expiry cleanup
CREATE INDEX idx_cache_expires ON cache_entries(expires_at) 
  WHERE expires_at IS NOT NULL;

-- Covering index so we don't have to touch the heap if it's a hit
CREATE INDEX idx_cache_lookup ON cache_entries(cache_key, scope_type, scope_value, paths_hash, input_hash)
  INCLUDE (storage_key, storage_bucket, status, expires_at);
