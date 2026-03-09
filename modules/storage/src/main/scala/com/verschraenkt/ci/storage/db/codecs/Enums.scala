/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
package com.verschraenkt.ci.storage.db.codecs

/** Trait for PostgreSQL ENUM types that provides `fromString` and `toDbString` from a single mapping.
  */
trait PgEnum[E]:
  protected def mapping: Seq[(E, String)]

  private lazy val toStringMap: Map[E, String]   = mapping.toMap
  private lazy val fromStringMap: Map[String, E]  = mapping.map(_.swap).toMap
  private lazy val typeName: String               = getClass.getSimpleName.stripSuffix("$")

  def fromString(s: String): E =
    fromStringMap.getOrElse(
      s.toLowerCase,
      throw new IllegalArgumentException(s"Unknown $typeName: $s")
    )

  extension (e: E) def toDbString: String = toStringMap(e)

/** PostgreSQL ENUM type definitions
  *
  * These enums correspond to the ENUM types defined in the database schema (1.sql)
  */
object Enums:

  /** Execution status for pipelines, workflows, jobs, and steps */
  enum ExecutionStatus:
    case Pending, Running, Completed, Failed, Cancelled, Timeout

  object ExecutionStatus extends PgEnum[ExecutionStatus]:
    protected val mapping: Seq[(ExecutionStatus, String)] = Seq(
      Pending   -> "pending",
      Running   -> "running",
      Completed -> "completed",
      Failed    -> "failed",
      Cancelled -> "cancelled",
      Timeout   -> "timeout"
    )

  /** Trigger type for executions */
  enum TriggerType:
    case Manual, Webhook, Schedule, Api

  object TriggerType extends PgEnum[TriggerType]:
    protected val mapping: Seq[(TriggerType, String)] = Seq(
      Manual   -> "manual",
      Webhook  -> "webhook",
      Schedule -> "schedule",
      Api      -> "api"
    )

  /** Step type for step executions */
  enum StepType:
    case Checkout, Run, CacheRestore, CacheSave, ArtifactUpload, ArtifactDownload, Composite

  object StepType extends PgEnum[StepType]:
    protected val mapping: Seq[(StepType, String)] = Seq(
      Checkout         -> "checkout",
      Run              -> "run",
      CacheRestore     -> "cache_restore",
      CacheSave        -> "cache_save",
      ArtifactUpload   -> "artifact_upload",
      ArtifactDownload -> "artifact_download",
      Composite        -> "composite"
    )

  /** Secret scope */
  enum SecretScope:
    case Global, Pipeline, Workflow, Job

  object SecretScope extends PgEnum[SecretScope]:
    protected val mapping: Seq[(SecretScope, String)] = Seq(
      Global   -> "global",
      Pipeline -> "pipeline",
      Workflow -> "workflow",
      Job      -> "job"
    )

  /** Storage backend type */
  enum StorageBackend:
    case S3, Minio, Gcs, AzureBlob

  object StorageBackend extends PgEnum[StorageBackend]:
    protected val mapping: Seq[(StorageBackend, String)] = Seq(
      S3        -> "s3",
      Minio     -> "minio",
      Gcs       -> "gcs",
      AzureBlob -> "azure_blob"
    )

  /** Executor status */
  enum ExecutorStatus:
    case Online, Offline, Draining

  object ExecutorStatus extends PgEnum[ExecutorStatus]:
    protected val mapping: Seq[(ExecutorStatus, String)] = Seq(
      Online   -> "online",
      Offline  -> "offline",
      Draining -> "draining"
    )

  /** Cache status */
  enum CacheStatus:
    case Creating, Ready, Failed, Deleting

  object CacheStatus extends PgEnum[CacheStatus]:
    protected val mapping: Seq[(CacheStatus, String)] = Seq(
      Creating -> "creating",
      Ready    -> "ready",
      Failed   -> "failed",
      Deleting -> "deleting"
    )

  /** Cache scope type */
  enum CacheScopeType:
    case Global, Branch, Pr, Repo, Commit

  object CacheScopeType extends PgEnum[CacheScopeType]:
    protected val mapping: Seq[(CacheScopeType, String)] = Seq(
      Global -> "global",
      Branch -> "branch",
      Pr     -> "pr",
      Repo   -> "repo",
      Commit -> "commit"
    )

  /** Actor type for audit log */
  enum ActorType:
    case User, Executor, System, ApiToken

  object ActorType extends PgEnum[ActorType]:
    protected val mapping: Seq[(ActorType, String)] = Seq(
      User     -> "user",
      Executor -> "executor",
      System   -> "system",
      ApiToken -> "api_token"
    )

  /** Platform type */
  enum Platform:
    case Linux, Windows64b, Windows32b, MacOS

  object Platform extends PgEnum[Platform]:
    protected val mapping: Seq[(Platform, String)] = Seq(
      Linux      -> "linux",
      Windows64b -> "win-x64",
      Windows32b -> "win-x86",
      MacOS      -> "darwin"
    )
