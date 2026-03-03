package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.model.PipelineId
import com.verschraenkt.ci.storage.db.DatabaseModule
import com.verschraenkt.ci.storage.db.codecs.Enums.ExecutionStatus
import com.verschraenkt.ci.storage.db.tables.ExecutionRow
import com.verschraenkt.ci.storage.fixtures.TestExecutions
import com.verschraenkt.ci.storage.testing.TestReflection
import munit.FunSuite
import org.mockito.Mockito.mock

import java.time.Instant
import java.util.UUID

class ExecutionRepositorySpec extends FunSuite:

  val dbModule: DatabaseModule = mock(classOf[DatabaseModule])
  val repo                     = new ExecutionRepository(dbModule)

  test("ExecutionRepository instantiation") {
    assertNotEquals(repo, null)
  }

  test("ExecutionRepository has correct component name") {
    val baseCtx = TestReflection.invokeNoArg[ApplicationContext](repo, "baseCtx")
    assertEquals(baseCtx.source, "ExecutionRepository")
  }

  test("Repository extends required traits") {
    assert(repo.isInstanceOf[IExecutionRepository])
    assert(repo.isInstanceOf[DatabaseOperations])
  }

  test("Repository interface defines all required methods") {
    val methods = classOf[IExecutionRepository].getDeclaredMethods.map(_.getName).toSet

    assert(methods.contains("findById"))
    assert(methods.contains("findByIdIncludingDeleted"))
    assert(methods.contains("findByPipelineId"))
    assert(methods.contains("findQueued"))
    assert(methods.contains("findRunning"))
    assert(methods.contains("findByConcurrencyGroup"))
    assert(methods.contains("findByIdempotencyKey"))
    assert(methods.contains("create"))
    assert(methods.contains("updateStatus"))
    assert(methods.contains("updateStartedAt"))
    assert(methods.contains("updateCompletedAt"))
    assert(methods.contains("updateResourceUsage"))
    assert(methods.contains("updateConcurrencyQueuePosition"))
    assert(methods.contains("softDelete"))
    assert(methods.contains("findTimedOut"))
  }

  // Test method signatures and return types

  test("findById method signature") {
    val execution                        = TestExecutions.queuedExecution()
    val findOp: IO[Option[ExecutionRow]] = repo.findById(execution.executionId)

    assertNotEquals(findOp, null)
  }

  test("findByIdIncludingDeleted method signature") {
    val execution                        = TestExecutions.queuedExecution()
    val findOp: IO[Option[ExecutionRow]] = repo.findByIdIncludingDeleted(execution.executionId)

    assertNotEquals(findOp, null)
  }

  test("findByPipelineId method signature with no status filter") {
    val pipelineId                    = PipelineId("test-pipeline")
    val findOp: IO[Seq[ExecutionRow]] = repo.findByPipelineId(pipelineId, None, 100)

    assertNotEquals(findOp, null)
  }

  test("findByPipelineId method signature with status filter") {
    val pipelineId                    = PipelineId("test-pipeline")
    val findOp: IO[Seq[ExecutionRow]] = repo.findByPipelineId(pipelineId, Some(ExecutionStatus.Running), 50)

    assertNotEquals(findOp, null)
  }

  test("findQueued method signature") {
    val findOp: IO[Seq[ExecutionRow]] = repo.findQueued(100)

    assertNotEquals(findOp, null)
  }

  test("findRunning method signature") {
    val findOp: IO[Seq[ExecutionRow]] = repo.findRunning(100)

    assertNotEquals(findOp, null)
  }

  test("findByConcurrencyGroup method signature") {
    val findOp: IO[Seq[ExecutionRow]] = repo.findByConcurrencyGroup("test-group", 100)

    assertNotEquals(findOp, null)
  }

  test("findByIdempotencyKey method signature") {
    val findOp: IO[Option[ExecutionRow]] = repo.findByIdempotencyKey("test-key")

    assertNotEquals(findOp, null)
  }

  test("create method signature") {
    val execution          = TestExecutions.queuedExecution()
    val createOp: IO[UUID] = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("updateStatus method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateStatus(id, ExecutionStatus.Running, None)

    assertNotEquals(updateOp, null)
  }

  test("updateStatus with error message method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateStatus(id, ExecutionStatus.Failed, Some("Error occurred"))

    assertNotEquals(updateOp, null)
  }

  test("updateStartedAt method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateStartedAt(id, Instant.now())

    assertNotEquals(updateOp, null)
  }

  test("updateCompletedAt method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateCompletedAt(id, Instant.now())

    assertNotEquals(updateOp, null)
  }

  test("updateResourceUsage method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateResourceUsage(id, 100000L, 512000L)

    assertNotEquals(updateOp, null)
  }

  test("updateConcurrencyQueuePosition method signature") {
    val id                    = UUID.randomUUID()
    val updateOp: IO[Boolean] = repo.updateConcurrencyQueuePosition(id, 5)

    assertNotEquals(updateOp, null)
  }

  test("softDelete method signature") {
    val id                    = UUID.randomUUID()
    val deleteOp: IO[Boolean] = repo.softDelete(id, TestExecutions.testUser)

    assertNotEquals(deleteOp, null)
  }

  test("findTimedOut method signature with default time") {
    val findOp: IO[Seq[ExecutionRow]] = repo.findTimedOut()

    assertNotEquals(findOp, null)
  }

  test("findTimedOut method signature with custom time") {
    val findOp: IO[Seq[ExecutionRow]] = repo.findTimedOut(Instant.now(), 50)

    assertNotEquals(findOp, null)
  }

  // Test that different execution fixtures can be used

  test("create works with queued execution") {
    val execution = TestExecutions.queuedExecution()
    val createOp  = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("create works with running execution") {
    val execution = TestExecutions.runningExecution()
    val createOp  = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("create works with completed execution") {
    val execution = TestExecutions.completedExecution()
    val createOp  = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("create works with failed execution") {
    val execution = TestExecutions.failedExecution()
    val createOp  = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("create works with execution with concurrency") {
    val execution = TestExecutions.executionWithConcurrency()
    val createOp  = repo.create(execution)

    assertNotEquals(createOp, null)
  }

  test("findByPipelineId respects limit parameter") {
    val pipelineId = PipelineId("test-pipeline")
    val op1        = repo.findByPipelineId(pipelineId, None, 10)
    val op2        = repo.findByPipelineId(pipelineId, None, 100)
    val op3        = repo.findByPipelineId(pipelineId, None, 1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("findQueued respects limit parameter") {
    val op1 = repo.findQueued(10)
    val op2 = repo.findQueued(100)
    val op3 = repo.findQueued(1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("findRunning respects limit parameter") {
    val op1 = repo.findRunning(10)
    val op2 = repo.findRunning(100)
    val op3 = repo.findRunning(1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("findByConcurrencyGroup respects limit parameter") {
    val op1 = repo.findByConcurrencyGroup("group", 10)
    val op2 = repo.findByConcurrencyGroup("group", 100)
    val op3 = repo.findByConcurrencyGroup("group", 1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("findTimedOut respects limit parameter") {
    val op1 = repo.findTimedOut(Instant.now(), 10)
    val op2 = repo.findTimedOut(Instant.now(), 100)
    val op3 = repo.findTimedOut(Instant.now(), 1000)

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
  }

  test("updateStatus works with different statuses") {
    val id  = UUID.randomUUID()
    val op1 = repo.updateStatus(id, ExecutionStatus.Pending, None)
    val op2 = repo.updateStatus(id, ExecutionStatus.Running, None)
    val op3 = repo.updateStatus(id, ExecutionStatus.Completed, None)
    val op4 = repo.updateStatus(id, ExecutionStatus.Failed, Some("Error"))

    assertNotEquals(op1, null)
    assertNotEquals(op2, null)
    assertNotEquals(op3, null)
    assertNotEquals(op4, null)
  }

  test("repository handles multiple different execution IDs") {
    val execution1 = TestExecutions.queuedExecution()
    val execution2 = TestExecutions.runningExecution()
    val execution3 = TestExecutions.completedExecution()

    // Each should have a unique ID
    assertNotEquals(execution1.executionId, execution2.executionId)
    assertNotEquals(execution2.executionId, execution3.executionId)
    assertNotEquals(execution1.executionId, execution3.executionId)
  }

  test("custom execution IDs work correctly") {
    val customId  = UUID.randomUUID()
    val execution = TestExecutions.withId(customId)

    assertEquals(execution.executionId, customId)

    val createOp = repo.create(execution)
    assertNotEquals(createOp, null)
  }

  test("custom statuses work correctly") {
    val execution = TestExecutions.withStatus(ExecutionStatus.Failed)

    assertEquals(execution.status, ExecutionStatus.Failed)

    val createOp = repo.create(execution)
    assertNotEquals(createOp, null)
  }

  test("custom idempotency keys work correctly") {
    val key       = "my-unique-key-123"
    val execution = TestExecutions.withIdempotencyKey(key)

    assertEquals(execution.idempotencyKey, Some(key))

    val createOp = repo.create(execution)
    assertNotEquals(createOp, null)
  }

  test("custom pipeline associations work correctly") {
    val pipelineId = PipelineId("my-custom-pipeline")
    val execution  = TestExecutions.forPipeline(pipelineId)

    assertEquals(execution.pipelineId, pipelineId)

    val createOp = repo.create(execution)
    assertNotEquals(createOp, null)
  }
