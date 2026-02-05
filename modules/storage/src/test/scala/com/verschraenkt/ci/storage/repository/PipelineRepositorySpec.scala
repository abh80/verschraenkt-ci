package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.TestPipelines
import com.verschraenkt.ci.storage.testing.TestReflection
import munit.FunSuite
import org.mockito.Mockito.mock

class PipelineRepositorySpec extends FunSuite:

  val dbModule: DatabaseModule = mock(classOf[DatabaseModule])
  val repo                     = new PipelineRepository(dbModule)

  test("PipelineRepository instantiation") {

    assertNotEquals(repo, null)
  }

  test("PipelineRepository has correct component name") {
    // cheat JVM just for this test lol
    val baseCtx = TestReflection.invokeNoArg[ApplicationContext](repo, "baseCtx")

    assertEquals(baseCtx.source, "PipelineRepository")
  }

  test("PipelineRepository save method signature") {

    // Verify that the save method exists with the correct signature
    val pipeline               = TestPipelines.simplePipeline
    val saveOp: IO[PipelineId] = repo.save(pipeline, "test-user")

    // We expect this to fail since we don't have a real database
    // but we're just testing the signature here
    assertNotEquals(saveOp, null)
  }

  test("Repository extends required traits") {

    assert(repo.isInstanceOf[IPipelineRepository])
    assert(repo.isInstanceOf[DatabaseOperations])
  }

  test("Repository interface defines all required methods") {
    // Verify the interface has all expected methods
    val methods = classOf[IPipelineRepository].getDeclaredMethods.map(_.getName).toSet

    assert(methods.contains("findById"))
    assert(methods.contains("findByIdIncludingDeleted"))
    assert(methods.contains("findActive"))
    assert(methods.contains("save"))
    assert(methods.contains("update"))
    assert(methods.contains("softDelete"))
    assert(methods.contains("getVersionHistory"))
  }
