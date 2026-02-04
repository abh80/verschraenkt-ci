package com.verschraenkt.ci.storage.db.codecs

/** PostgreSQL ENUM type definitions
  *
  * These enums correspond to the ENUM types defined in the database schema (1.sql)
  */
object Enums:

  /** Execution status for pipelines, workflows, jobs, and steps */
  enum ExecutionStatus:
    case Pending, Running, Completed, Failed, Cancelled, Timeout

  object ExecutionStatus:
    def fromString(s: String): ExecutionStatus = s.toLowerCase match
      case "pending"   => Pending
      case "running"   => Running
      case "completed" => Completed
      case "failed"    => Failed
      case "cancelled" => Cancelled
      case "timeout"   => Timeout
      case _           => throw new IllegalArgumentException(s"Unknown execution status: $s")

    extension (status: ExecutionStatus)
      def toDbString: String = status match
        case Pending   => "pending"
        case Running   => "running"
        case Completed => "completed"
        case Failed    => "failed"
        case Cancelled => "cancelled"
        case Timeout   => "timeout"

  /** Trigger type for executions */
  enum TriggerType:
    case Manual, Webhook, Schedule, Api

  object TriggerType:
    def fromString(s: String): TriggerType = s.toLowerCase match
      case "manual"   => Manual
      case "webhook"  => Webhook
      case "schedule" => Schedule
      case "api"      => Api
      case _          => throw new IllegalArgumentException(s"Unknown trigger type: $s")

    extension (trigger: TriggerType)
      def toDbString: String = trigger match
        case Manual   => "manual"
        case Webhook  => "webhook"
        case Schedule => "schedule"
        case Api      => "api"

  /** Step type for step executions */
  enum StepType:
    case Checkout, Run, CacheRestore, CacheSave, ArtifactUpload, ArtifactDownload, Composite

  object StepType:
    def fromString(s: String): StepType = s.toLowerCase match
      case "checkout"          => Checkout
      case "run"               => Run
      case "cache_restore"     => CacheRestore
      case "cache_save"        => CacheSave
      case "artifact_upload"   => ArtifactUpload
      case "artifact_download" => ArtifactDownload
      case "composite"         => Composite
      case _                   => throw new IllegalArgumentException(s"Unknown step type: $s")

    extension (stepType: StepType)
      def toDbString: String = stepType match
        case Checkout         => "checkout"
        case Run              => "run"
        case CacheRestore     => "cache_restore"
        case CacheSave        => "cache_save"
        case ArtifactUpload   => "artifact_upload"
        case ArtifactDownload => "artifact_download"
        case Composite        => "composite"

  /** Secret scope */
  enum SecretScope:
    case Global, Pipeline, Workflow, Job

  object SecretScope:
    def fromString(s: String): SecretScope = s.toLowerCase match
      case "global"   => Global
      case "pipeline" => Pipeline
      case "workflow" => Workflow
      case "job"      => Job
      case _          => throw new IllegalArgumentException(s"Unknown secret scope: $s")

    extension (scope: SecretScope)
      def toDbString: String = scope match
        case Global   => "global"
        case Pipeline => "pipeline"
        case Workflow => "workflow"
        case Job      => "job"

  /** Storage backend type */
  enum StorageBackend:
    case S3, Minio, Gcs, AzureBlob

  object StorageBackend:
    def fromString(s: String): StorageBackend = s.toLowerCase match
      case "s3"         => S3
      case "minio"      => Minio
      case "gcs"        => Gcs
      case "azure_blob" => AzureBlob
      case _            => throw new IllegalArgumentException(s"Unknown storage backend: $s")

    extension (backend: StorageBackend)
      def toDbString: String = backend match
        case S3        => "s3"
        case Minio     => "minio"
        case Gcs       => "gcs"
        case AzureBlob => "azure_blob"

  /** Executor status */
  enum ExecutorStatus:
    case Online, Offline, Draining

  object ExecutorStatus:
    def fromString(s: String): ExecutorStatus = s.toLowerCase match
      case "online"   => Online
      case "offline"  => Offline
      case "draining" => Draining
      case _          => throw new IllegalArgumentException(s"Unknown executor status: $s")

    extension (status: ExecutorStatus)
      def toDbString: String = status match
        case Online   => "online"
        case Offline  => "offline"
        case Draining => "draining"

  /** Cache status */
  enum CacheStatus:
    case Creating, Ready, Failed, Deleting

  object CacheStatus:
    def fromString(s: String): CacheStatus = s.toLowerCase match
      case "creating" => Creating
      case "ready"    => Ready
      case "failed"   => Failed
      case "deleting" => Deleting
      case _          => throw new IllegalArgumentException(s"Unknown cache status: $s")

    extension (status: CacheStatus)
      def toDbString: String = status match
        case Creating => "creating"
        case Ready    => "ready"
        case Failed   => "failed"
        case Deleting => "deleting"

  /** Cache scope type */
  enum CacheScopeType:
    case Global, Branch, Pr, Repo, Commit

  object CacheScopeType:
    def fromString(s: String): CacheScopeType = s.toLowerCase match
      case "global" => Global
      case "branch" => Branch
      case "pr"     => Pr
      case "repo"   => Repo
      case "commit" => Commit
      case _        => throw new IllegalArgumentException(s"Unknown cache scope type: $s")

    extension (scopeType: CacheScopeType)
      def toDbString: String = scopeType match
        case Global => "global"
        case Branch => "branch"
        case Pr     => "pr"
        case Repo   => "repo"
        case Commit => "commit"

  /** Actor type for audit log */
  enum ActorType:
    case User, Executor, System, ApiToken

  object ActorType:
    def fromString(s: String): ActorType = s.toLowerCase match
      case "user"      => User
      case "executor"  => Executor
      case "system"    => System
      case "api_token" => ApiToken
      case _           => throw new IllegalArgumentException(s"Unknown actor type: $s")

    extension (actorType: ActorType)
      def toDbString: String = actorType match
        case User     => "user"
        case Executor => "executor"
        case System   => "system"
        case ApiToken => "api_token"
