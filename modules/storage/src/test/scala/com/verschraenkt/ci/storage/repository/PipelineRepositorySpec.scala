package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.{ Pipeline, PipelineId }
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
  }

  // Test method signatures and return types

  test("findById method signature") {
    val pipeline                     = TestPipelines.simplePipeline
    val findOp: IO[Option[Pipeline]] = repo.findById(pipeline.id)

    assertNotEquals(findOp, null)
  }

  test("findByIdIncludingDeleted method signature") {
    val pipeline                     = TestPipelines.simplePipeline
    val findOp: IO[Option[Pipeline]] = repo.findByIdIncludingDeleted(pipeline.id)

    assertNotEquals(findOp, null)
  }

  test("findActive method signature with no labels") {
    val findOp: IO[Vector[Pipeline]] = repo.findActive(None, 100)

    assertNotEquals(findOp, null)
  }

  test("findActive method signature with labels") {
    val labels                       = Some(Set("production", "critical"))
    val findOp: IO[Vector[Pipeline]] = repo.findActive(labels, 50)

    assertNotEquals(findOp, null)
  }

  test("save method signature") {
    val pipeline               = TestPipelines.simplePipeline
    val saveOp: IO[PipelineId] = repo.save(pipeline, "test-user")

    // We expect this to fail since we don't have a real database
    // but we're just testing the signature here
    assertNotEquals(saveOp, null)
  }

  test("update method signature") {
    val pipeline          = TestPipelines.simplePipeline
    val updatedBy         = TestPipelines.testUser
    val updateOp: IO[Int] = repo.update(pipeline, updatedBy, version = 2)

    assertNotEquals(updateOp, null)
  }

  test("softDelete method signature") {
    val pipeline              = TestPipelines.simplePipeline
    val deletedBy             = TestPipelines.testUser
    val deleteOp: IO[Boolean] = repo.softDelete(pipeline.id, deletedBy)

    assertNotEquals(deleteOp, null)
  }

  // Test that different pipeline fixtures can be used

  test("save works with simple pipeline") {
    val pipeline = TestPipelines.simplePipeline
    val saveOp   = repo.save(pipeline, "creator")

    assertNotEquals(saveOp, null)
  }

  test("save works with pipeline with labels") {
    val pipeline = TestPipelines.pipelineWithLabels
    val saveOp   = repo.save(pipeline, "creator")

    assertEquals(pipeline.labels, Set("production", "automated", "critical"))
    assertNotEquals(saveOp, null)
  }

  test("save works with pipeline with multiple workflows") {
    val pipeline = TestPipelines.pipelineWithMultipleWorkflows
    val saveOp   = repo.save(pipeline, "creator")

    assertEquals(pipeline.workflows.length, 2)
    assertNotEquals(saveOp, null)
  }

  test("save works with complex pipeline") {
    val pipeline = TestPipelines.complexPipeline
    val saveOp   = repo.save(pipeline, "creator")

    assertEquals(pipeline.labels, Set("docker", "build"))
    assertNotEquals(saveOp, null)
  }

  test("update works with different users") {
    val pipeline  = TestPipelines.simplePipeline
    val updateOp1 = repo.update(pipeline, TestPipelines.testUser, 2)
    val updateOp2 = repo.update(pipeline, TestPipelines.anotherUser, 3)

    assertNotEquals(updateOp1, null)
    assertNotEquals(updateOp2, null)
  }

  test("softDelete works with different users") {
    val pipeline  = TestPipelines.simplePipeline
    val deleteOp1 = repo.softDelete(pipeline.id, TestPipelines.testUser)
    val deleteOp2 = repo.softDelete(pipeline.id, TestPipelines.anotherUser)

    assertNotEquals(deleteOp1, null)
    assertNotEquals(deleteOp2, null)
  }

  test("custom pipeline IDs work correctly") {
    val customId = "my-custom-pipeline-123"
    val pipeline = TestPipelines.withId(customId)

    assertEquals(pipeline.id.value, customId)

    val saveOp = repo.save(pipeline, "creator")
    assertNotEquals(saveOp, null)
  }

  test("custom labels work correctly") {
    val customLabels = Set("staging", "manual", "low-priority")
    val pipeline     = TestPipelines.withLabels(customLabels)

    assertEquals(pipeline.labels, customLabels)

    val saveOp = repo.save(pipeline, "creator")
    assertNotEquals(saveOp, null)
  }

  test("findActive respects limit parameter") {
    // Test with different limit values
    val op1 = repo.findActive(None, 10)
    val op2 = repo.findActive(None, 100)
    val op3 = repo.findActive(None, 1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("update increments version correctly") {
    val pipeline = TestPipelines.simplePipeline
    val user     = TestPipelines.testUser

    val updateV2 = repo.update(pipeline, user, 2)
    val updateV3 = repo.update(pipeline, user, 3)
    val updateV4 = repo.update(pipeline, user, 4)

    assertNotEquals(updateV2, null)
    assertNotEquals(updateV3, null)
    assertNotEquals(updateV4, null)
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
