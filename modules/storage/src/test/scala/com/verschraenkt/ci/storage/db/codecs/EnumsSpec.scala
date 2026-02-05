package com.verschraenkt.ci.storage.db.codecs

import munit.FunSuite

class EnumsSpec extends FunSuite:
  import Enums.*

  // ExecutionStatus Tests

  test("ExecutionStatus.fromString parses all valid values") {
    assertEquals(ExecutionStatus.fromString("pending"), ExecutionStatus.Pending)
    assertEquals(ExecutionStatus.fromString("running"), ExecutionStatus.Running)
    assertEquals(ExecutionStatus.fromString("completed"), ExecutionStatus.Completed)
    assertEquals(ExecutionStatus.fromString("failed"), ExecutionStatus.Failed)
    assertEquals(ExecutionStatus.fromString("cancelled"), ExecutionStatus.Cancelled)
    assertEquals(ExecutionStatus.fromString("timeout"), ExecutionStatus.Timeout)
  }

  test("ExecutionStatus.fromString is case-insensitive") {
    assertEquals(ExecutionStatus.fromString("PENDING"), ExecutionStatus.Pending)
    assertEquals(ExecutionStatus.fromString("Running"), ExecutionStatus.Running)
    assertEquals(ExecutionStatus.fromString("COMPLETED"), ExecutionStatus.Completed)
  }

  test("ExecutionStatus.toDbString converts to database string") {
    assertEquals(ExecutionStatus.Pending.toDbString, "pending")
    assertEquals(ExecutionStatus.Running.toDbString, "running")
    assertEquals(ExecutionStatus.Completed.toDbString, "completed")
    assertEquals(ExecutionStatus.Failed.toDbString, "failed")
    assertEquals(ExecutionStatus.Cancelled.toDbString, "cancelled")
    assertEquals(ExecutionStatus.Timeout.toDbString, "timeout")
  }

  test("ExecutionStatus round-trip conversion") {
    ExecutionStatus.values.foreach { status =>
      val dbString = status.toDbString
      val parsed   = ExecutionStatus.fromString(dbString)
      assertEquals(parsed, status)
    }
  }

  test("ExecutionStatus.fromString throws on invalid value") {
    intercept[IllegalArgumentException] {
      ExecutionStatus.fromString("invalid")
    }
  }

  // TriggerType Tests

  test("TriggerType.fromString parses all valid values") {
    assertEquals(TriggerType.fromString("manual"), TriggerType.Manual)
    assertEquals(TriggerType.fromString("webhook"), TriggerType.Webhook)
    assertEquals(TriggerType.fromString("schedule"), TriggerType.Schedule)
    assertEquals(TriggerType.fromString("api"), TriggerType.Api)
  }

  test("TriggerType.toDbString converts to database string") {
    assertEquals(TriggerType.Manual.toDbString, "manual")
    assertEquals(TriggerType.Webhook.toDbString, "webhook")
    assertEquals(TriggerType.Schedule.toDbString, "schedule")
    assertEquals(TriggerType.Api.toDbString, "api")
  }

  test("TriggerType round-trip conversion") {
    TriggerType.values.foreach { trigger =>
      val dbString = trigger.toDbString
      val parsed   = TriggerType.fromString(dbString)
      assertEquals(parsed, trigger)
    }
  }

  // StepType Tests

  test("StepType.fromString parses all valid values") {
    assertEquals(StepType.fromString("checkout"), StepType.Checkout)
    assertEquals(StepType.fromString("run"), StepType.Run)
    assertEquals(StepType.fromString("cache_restore"), StepType.CacheRestore)
    assertEquals(StepType.fromString("cache_save"), StepType.CacheSave)
    assertEquals(StepType.fromString("artifact_upload"), StepType.ArtifactUpload)
    assertEquals(StepType.fromString("artifact_download"), StepType.ArtifactDownload)
    assertEquals(StepType.fromString("composite"), StepType.Composite)
  }

  test("StepType.toDbString converts to database string") {
    assertEquals(StepType.Checkout.toDbString, "checkout")
    assertEquals(StepType.Run.toDbString, "run")
    assertEquals(StepType.CacheRestore.toDbString, "cache_restore")
    assertEquals(StepType.CacheSave.toDbString, "cache_save")
    assertEquals(StepType.ArtifactUpload.toDbString, "artifact_upload")
    assertEquals(StepType.ArtifactDownload.toDbString, "artifact_download")
    assertEquals(StepType.Composite.toDbString, "composite")
  }

  test("StepType round-trip conversion") {
    StepType.values.foreach { stepType =>
      val dbString = stepType.toDbString
      val parsed   = StepType.fromString(dbString)
      assertEquals(parsed, stepType)
    }
  }

  // SecretScope Tests

  test("SecretScope.fromString parses all valid values") {
    assertEquals(SecretScope.fromString("global"), SecretScope.Global)
    assertEquals(SecretScope.fromString("pipeline"), SecretScope.Pipeline)
    assertEquals(SecretScope.fromString("workflow"), SecretScope.Workflow)
    assertEquals(SecretScope.fromString("job"), SecretScope.Job)
  }

  test("SecretScope.toDbString converts to database string") {
    assertEquals(SecretScope.Global.toDbString, "global")
    assertEquals(SecretScope.Pipeline.toDbString, "pipeline")
    assertEquals(SecretScope.Workflow.toDbString, "workflow")
    assertEquals(SecretScope.Job.toDbString, "job")
  }

  test("SecretScope round-trip conversion") {
    SecretScope.values.foreach { scope =>
      val dbString = scope.toDbString
      val parsed   = SecretScope.fromString(dbString)
      assertEquals(parsed, scope)
    }
  }

  // StorageBackend Tests

  test("StorageBackend.fromString parses all valid values") {
    assertEquals(StorageBackend.fromString("s3"), StorageBackend.S3)
    assertEquals(StorageBackend.fromString("minio"), StorageBackend.Minio)
    assertEquals(StorageBackend.fromString("gcs"), StorageBackend.Gcs)
    assertEquals(StorageBackend.fromString("azure_blob"), StorageBackend.AzureBlob)
  }

  test("StorageBackend.toDbString converts to database string") {
    assertEquals(StorageBackend.S3.toDbString, "s3")
    assertEquals(StorageBackend.Minio.toDbString, "minio")
    assertEquals(StorageBackend.Gcs.toDbString, "gcs")
    assertEquals(StorageBackend.AzureBlob.toDbString, "azure_blob")
  }

  test("StorageBackend round-trip conversion") {
    StorageBackend.values.foreach { backend =>
      val dbString = backend.toDbString
      val parsed   = StorageBackend.fromString(dbString)
      assertEquals(parsed, backend)
    }
  }

  // ExecutorStatus Tests

  test("ExecutorStatus.fromString parses all valid values") {
    assertEquals(ExecutorStatus.fromString("online"), ExecutorStatus.Online)
    assertEquals(ExecutorStatus.fromString("offline"), ExecutorStatus.Offline)
    assertEquals(ExecutorStatus.fromString("draining"), ExecutorStatus.Draining)
  }

  test("ExecutorStatus.toDbString converts to database string") {
    assertEquals(ExecutorStatus.Online.toDbString, "online")
    assertEquals(ExecutorStatus.Offline.toDbString, "offline")
    assertEquals(ExecutorStatus.Draining.toDbString, "draining")
  }

  test("ExecutorStatus round-trip conversion") {
    ExecutorStatus.values.foreach { status =>
      val dbString = status.toDbString
      val parsed   = ExecutorStatus.fromString(dbString)
      assertEquals(parsed, status)
    }
  }

  // CacheStatus Tests

  test("CacheStatus.fromString parses all valid values") {
    assertEquals(CacheStatus.fromString("creating"), CacheStatus.Creating)
    assertEquals(CacheStatus.fromString("ready"), CacheStatus.Ready)
    assertEquals(CacheStatus.fromString("failed"), CacheStatus.Failed)
    assertEquals(CacheStatus.fromString("deleting"), CacheStatus.Deleting)
  }

  test("CacheStatus.toDbString converts to database string") {
    assertEquals(CacheStatus.Creating.toDbString, "creating")
    assertEquals(CacheStatus.Ready.toDbString, "ready")
    assertEquals(CacheStatus.Failed.toDbString, "failed")
    assertEquals(CacheStatus.Deleting.toDbString, "deleting")
  }

  test("CacheStatus round-trip conversion") {
    CacheStatus.values.foreach { status =>
      val dbString = status.toDbString
      val parsed   = CacheStatus.fromString(dbString)
      assertEquals(parsed, status)
    }
  }

  // CacheScopeType Tests

  test("CacheScopeType.fromString parses all valid values") {
    assertEquals(CacheScopeType.fromString("global"), CacheScopeType.Global)
    assertEquals(CacheScopeType.fromString("branch"), CacheScopeType.Branch)
    assertEquals(CacheScopeType.fromString("pr"), CacheScopeType.Pr)
    assertEquals(CacheScopeType.fromString("repo"), CacheScopeType.Repo)
    assertEquals(CacheScopeType.fromString("commit"), CacheScopeType.Commit)
  }

  test("CacheScopeType.toDbString converts to database string") {
    assertEquals(CacheScopeType.Global.toDbString, "global")
    assertEquals(CacheScopeType.Branch.toDbString, "branch")
    assertEquals(CacheScopeType.Pr.toDbString, "pr")
    assertEquals(CacheScopeType.Repo.toDbString, "repo")
    assertEquals(CacheScopeType.Commit.toDbString, "commit")
  }

  test("CacheScopeType round-trip conversion") {
    CacheScopeType.values.foreach { scopeType =>
      val dbString = scopeType.toDbString
      val parsed   = CacheScopeType.fromString(dbString)
      assertEquals(parsed, scopeType)
    }
  }

  // ActorType Tests

  test("ActorType.fromString parses all valid values") {
    assertEquals(ActorType.fromString("user"), ActorType.User)
    assertEquals(ActorType.fromString("executor"), ActorType.Executor)
    assertEquals(ActorType.fromString("system"), ActorType.System)
    assertEquals(ActorType.fromString("api_token"), ActorType.ApiToken)
  }

  test("ActorType.toDbString converts to database string") {
    assertEquals(ActorType.User.toDbString, "user")
    assertEquals(ActorType.Executor.toDbString, "executor")
    assertEquals(ActorType.System.toDbString, "system")
    assertEquals(ActorType.ApiToken.toDbString, "api_token")
  }

  test("ActorType round-trip conversion") {
    ActorType.values.foreach { actorType =>
      val dbString = actorType.toDbString
      val parsed   = ActorType.fromString(dbString)
      assertEquals(parsed, actorType)
    }
  }
