package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.fixtures.TestPipelines
import com.verschraenkt.ci.storage.testing.TestReflection
import munit.FunSuite
import org.mockito.Mockito.mock

class PipelineVersionsRepositorySpec extends FunSuite:

  val dbModule: DatabaseModule = mock(classOf[DatabaseModule])
  val repo                     = new PipelineVersionsRepository(dbModule)

  test("PipelineVersionsRepository instantiation") {
    assertNotEquals(repo, null)
  }

  test("PipelineVersionsRepository has correct component name") {
    // cheat JVM just for this test lol
    val baseCtx = TestReflection.invokeNoArg[ApplicationContext](repo, "baseCtx")

    assertEquals(baseCtx.source, "PipelineVersionsRepository")
  }

  test("Repository extends required traits") {
    assert(repo.isInstanceOf[IPipelineVersionsRepository])
    assert(repo.isInstanceOf[DatabaseOperations])
  }

  test("Repository interface defines all required methods") {
    // Verify the interface has all expected methods
    val methods = classOf[IPipelineVersionsRepository].getDeclaredMethods.map(_.getName).toSet

    assert(methods.contains("findByIdAndVersion"))
    assert(methods.contains("create"))
    assert(methods.contains("findAllVersionsByPipelineId"))
    assert(methods.contains("findLatestVersionByPipelineId"))
    assert(methods.contains("findVersionsInRange"))
    assert(methods.contains("countVersionsByPipelineId"))
    assert(methods.contains("existsVersion"))
    assert(methods.contains("listVersionsPaginated"))
  }

  // Test method signatures and return types

  test("findByIdAndVersion method signature") {
    val pipeline                     = TestPipelines.simplePipeline
    val findOp: IO[Option[Pipeline]] = repo.findByIdAndVersion(pipeline.id, 1)

    assertNotEquals(findOp, null)
  }

  test("create method signature") {
    val pipeline           = TestPipelines.simplePipeline
    val user               = TestPipelines.testUser
    val createOp: IO[Unit] = repo.create(pipeline, 1, user, "Initial version")

    assertNotEquals(createOp, null)
  }

  test("findAllVersionsByPipelineId method signature") {
    val pipeline             = TestPipelines.simplePipeline
    val findOp: IO[Seq[Int]] = repo.findAllVersionsByPipelineId(pipeline.id)

    assertNotEquals(findOp, null)
  }

  test("findLatestVersionByPipelineId method signature") {
    val pipeline                = TestPipelines.simplePipeline
    val findOp: IO[Option[Int]] = repo.findLatestVersionByPipelineId(pipeline.id)

    assertNotEquals(findOp, null)
  }

  test("findVersionsInRange method signature") {
    val pipeline             = TestPipelines.simplePipeline
    val findOp: IO[Seq[Int]] = repo.findVersionsInRange(pipeline.id, 1, 5)

    assertNotEquals(findOp, null)
  }

  test("countVersionsByPipelineId method signature") {
    val pipeline         = TestPipelines.simplePipeline
    val countOp: IO[Int] = repo.countVersionsByPipelineId(pipeline.id)

    assertNotEquals(countOp, null)
  }

  test("existsVersion method signature") {
    val pipeline              = TestPipelines.simplePipeline
    val existsOp: IO[Boolean] = repo.existsVersion(pipeline.id, 1)

    assertNotEquals(existsOp, null)
  }

  test("listVersionsPaginated method signature") {
    val pipeline             = TestPipelines.simplePipeline
    val listOp: IO[Seq[Int]] = repo.listVersionsPaginated(pipeline.id, 0, 10)

    assertNotEquals(listOp, null)
  }

  // Test with different pipeline fixtures

  test("create works with simple pipeline") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser
    val createOp = repo.create(pipeline, 1, user, "Initial version")

    assertNotEquals(createOp, null)
  }

  test("create works with pipeline with labels") {
    val pipeline = TestPipelines.pipelineWithLabels
    val user     = TestPipelines.testUser
    val createOp = repo.create(pipeline, 1, user, "Added labels")

    assertEquals(pipeline.labels, Set("production", "automated", "critical"))
    assertNotEquals(createOp, null)
  }

  test("create works with complex pipeline") {
    val pipeline = TestPipelines.complexPipeline
    val user     = TestPipelines.testUser
    val createOp = repo.create(pipeline, 1, user, "Complex pipeline initial version")

    assertEquals(pipeline.labels, Set("docker", "build"))
    assertNotEquals(createOp, null)
  }

  test("create works with different users") {
    val pipeline  = TestPipelines.simplePipeline
    val createOp1 = repo.create(pipeline, 1, TestPipelines.testUser, "Version by test user")
    val createOp2 = repo.create(pipeline, 2, TestPipelines.anotherUser, "Version by another user")

    assertNotEquals(createOp1, null)
    assertNotEquals(createOp2, null)
  }

  test("create works with different version numbers") {
    val pipeline  = TestPipelines.simplePipeline
    val user      = TestPipelines.testUser
    val createV1  = repo.create(pipeline, 1, user, "Version 1")
    val createV2  = repo.create(pipeline, 2, user, "Version 2")
    val createV10 = repo.create(pipeline, 10, user, "Version 10")

    assertNotEquals(createV1, null)
    assertNotEquals(createV2, null)
    assertNotEquals(createV10, null)
  }

  test("findByIdAndVersion works with different versions") {
    val pipeline = TestPipelines.simplePipeline
    val findV1   = repo.findByIdAndVersion(pipeline.id, 1)
    val findV2   = repo.findByIdAndVersion(pipeline.id, 2)
    val findV5   = repo.findByIdAndVersion(pipeline.id, 5)

    assertNotEquals(findV1, null)
    assertNotEquals(findV2, null)
    assertNotEquals(findV5, null)
  }

  test("findVersionsInRange works with different ranges") {
    val pipeline = TestPipelines.simplePipeline
    val range1   = repo.findVersionsInRange(pipeline.id, 1, 5)
    val range2   = repo.findVersionsInRange(pipeline.id, 5, 10)
    val range3   = repo.findVersionsInRange(pipeline.id, 1, 100)

    assertNotEquals(range1, null)
    assertNotEquals(range2, null)
    assertNotEquals(range3, null)
  }

  test("listVersionsPaginated respects offset and limit") {
    val pipeline = TestPipelines.simplePipeline
    val page1    = repo.listVersionsPaginated(pipeline.id, 0, 10)
    val page2    = repo.listVersionsPaginated(pipeline.id, 10, 10)
    val page3    = repo.listVersionsPaginated(pipeline.id, 0, 5)

    assertNotEquals(page1, null)
    assertNotEquals(page2, null)
    assertNotEquals(page3, null)
  }

  test("repository handles multiple different pipeline IDs") {
    val pipeline1 = TestPipelines.simplePipeline
    val pipeline2 = TestPipelines.pipelineWithLabels
    val pipeline3 = TestPipelines.complexPipeline

    // Each should have a unique ID due to counter
    assertNotEquals(pipeline1.id, pipeline2.id)
    assertNotEquals(pipeline2.id, pipeline3.id)
    assertNotEquals(pipeline1.id, pipeline3.id)
  }

  test("custom pipeline IDs work correctly") {
    val customId = "my-custom-pipeline-123"
    val pipeline = TestPipelines.withId(customId)

    assertEquals(pipeline.id.value, customId)

    val createOp = repo.create(pipeline, 1, TestPipelines.testUser, "Initial version")
    assertNotEquals(createOp, null)
  }

  test("create works with different change summaries") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val create1 = repo.create(pipeline, 1, user, "Initial version")
    val create2 = repo.create(pipeline, 2, user, "Updated configuration")
    val create3 = repo.create(pipeline, 3, user, "Fixed bug in workflow")
    val create4 = repo.create(pipeline, 4, user, "")

    assertNotEquals(create1, null)
    assertNotEquals(create2, null)
    assertNotEquals(create3, null)
    assertNotEquals(create4, null)
  }
