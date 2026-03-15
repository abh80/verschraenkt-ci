package com.verschraenkt.ci.storage.db.tables

import com.verschraenkt.ci.storage.db.codecs.Enums.SecretScope
import com.verschraenkt.ci.storage.fixtures.TestSecrets
import munit.FunSuite

import java.time.Instant

class SecretTableSpec extends FunSuite:

  test("SecretRow creates correct row with all fields") {
    val row = TestSecrets.secret()

    assertEquals(row.secretId, None)
    assert(row.name.startsWith("secret-"))
    assertEquals(row.scope, SecretScope.Global)
    assertEquals(row.scopeEntityId, None)
    assert(row.vaultPathEncrypted.nonEmpty)
    assert(row.vaultPathKeyId.nonEmpty)
    assertEquals(row.version, 1)
    assertEquals(row.isActive, true)
    assertEquals(row.deletedAt, None)
    assertEquals(row.deletedBy, None)
  }

  test("SecretRow handles pipeline-scoped secret") {
    val row = TestSecrets.pipelineScopedSecret(pipelineId = "my-pipeline")

    assertEquals(row.scope, SecretScope.Pipeline)
    assertEquals(row.scopeEntityId, Some("my-pipeline"))
  }

  test("SecretRow handles inactive secret") {
    val row = TestSecrets.inactiveSecret()

    assertEquals(row.isActive, false)
  }

  test("SecretRow handles rotated secret") {
    val row = TestSecrets.rotatedSecret()

    assertEquals(row.version, 2)
    assert(row.rotatedAt.isDefined)
    assert(row.rotatedAt.get.isBefore(Instant.now()))
  }

  test("SecretRow handles deleted secret") {
    val row = TestSecrets.deletedSecret()

    assertEquals(row.isActive, false)
    assert(row.deletedAt.isDefined)
    assertEquals(row.deletedBy, Some("admin-user"))
  }

  test("SecretRow preserves specific name") {
    val row = TestSecrets.secret(name = "my-api-key")

    assertEquals(row.name, "my-api-key")
  }

  test("SecretRow handles different scopes") {
    val global   = TestSecrets.secret(scope = SecretScope.Global)
    val pipeline = TestSecrets.secret(scope = SecretScope.Pipeline)
    val workflow = TestSecrets.secret(scope = SecretScope.Workflow)
    val job      = TestSecrets.secret(scope = SecretScope.Job)

    assertEquals(global.scope, SecretScope.Global)
    assertEquals(pipeline.scope, SecretScope.Pipeline)
    assertEquals(workflow.scope, SecretScope.Workflow)
    assertEquals(job.scope, SecretScope.Job)
  }

  test("SecretAccessLogRow creates correct granted entry") {
    val row = TestSecrets.accessLog()

    assertEquals(row.logId, None)
    assertEquals(row.granted, true)
    assertEquals(row.denialReason, None)
    assert(row.requestIp.isDefined)
  }

  test("SecretAccessLogRow creates correct denied entry") {
    val row = TestSecrets.deniedAccessLog(reason = "Not authorized")

    assertEquals(row.granted, false)
    assertEquals(row.denialReason, Some("Not authorized"))
  }
