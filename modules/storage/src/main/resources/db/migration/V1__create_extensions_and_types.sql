/*
 * VerschraenktCI Database Schema - Extensions and Types
 * 
 * This migration creates the necessary PostgreSQL extensions and custom types (enums)
 * used throughout the CI platform schema.
 */

-- Enable pgcrypto extension for UUID generation and cryptographic functions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Execution status tracking across all execution levels
CREATE TYPE execution_status AS ENUM (
  'pending', 
  'running', 
  'completed', 
  'failed', 
  'cancelled', 
  'timeout'
);

-- How was this execution triggered?
CREATE TYPE trigger_type AS ENUM (
  'manual',
  'webhook',
  'schedule',
  'api'
);

-- Different types of steps in a job
CREATE TYPE step_type AS ENUM (
  'checkout',
  'run',
  'cache_restore',
  'cache_save',
  'artifact_upload',
  'artifact_download',
  'composite'
);

-- Secret visibility scope
CREATE TYPE secret_scope AS ENUM (
  'global',
  'pipeline',
  'workflow',
  'job'
);

-- Supported storage backends for artifacts and cache
CREATE TYPE storage_backend AS ENUM (
  's3',
  'minio',
  'gcs',
  'azure_blob'
);

-- Executor health status
CREATE TYPE executor_status AS ENUM (
  'online',
  'offline',
  'draining'
);

-- Cache entry lifecycle status
CREATE TYPE cache_status AS ENUM (
  'creating',
  'ready',
  'failed',
  'deleting'
);

-- Cache visibility and isolation level
CREATE TYPE cache_scope_type AS ENUM (
  'global',
  'branch',
  'pr',
  'repo',
  'commit'
);

-- Who performed this action?
CREATE TYPE actor_type AS ENUM (
  'user',
  'executor',
  'system',
  'api_token'
);
