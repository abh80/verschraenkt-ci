package com.verschraenkt.ci.storage.context

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.testing.TestReflection
import munit.FunSuite

class StorageContextSpec extends FunSuite:

  // Test implementation of StorageContext
  class TestStorageComponent extends StorageContext:
    override protected def componentName: String = "TestComponent"

  val testComponent = new TestStorageComponent
// Reflection zone! An exception has been made to allow these for testing only, Do not use in production!
  private val componentName: String =
    TestReflection.invokeNoArg[String](testComponent, "componentName")

  private val baseCtx: ApplicationContext =
    TestReflection.invokeNoArg[ApplicationContext](testComponent, "baseCtx")

  private val getOperationCtx: String => ApplicationContext = (op: String) =>
    TestReflection.invokeArgs[ApplicationContext](testComponent, "withOperation", op)

  test("componentName can be overridden") {
    assertEquals(componentName, "TestComponent")
  }

  test("baseCtx is created with component name") {
    assertEquals(baseCtx.source, "TestComponent")
  }

  test("withOperation creates child context with operation name") {
    val operationCtx = getOperationCtx("save")
    assertEquals(operationCtx.source, "save")
  }

  test("contextualize enriches error with context") {
    given ctx: ApplicationContext = getOperationCtx("findById")

    val error = StorageError.NotFound("Pipeline", "pipeline-123")
    val contextualized =
      TestReflection.invokeArgs[StorageError.NotFound](testComponent, "contextualize", error, ctx)

    // The error should now have context attached
    assert(contextualized.source.isDefined || contextualized.location.isDefined)
  }

  test("fail raises contextualized error in IO") {
    given ctx: ApplicationContext = getOperationCtx("delete")

    val error = StorageError.NotFound("Job", "job-456")
    val result =
      TestReflection.invokeArgs[IO[String]](testComponent, "fail", error, ctx).attempt.unsafeRunSync()

    assert(result.isLeft)
    result.left.foreach { e =>
      assert(e.isInstanceOf[StorageError.NotFound])
      val notFoundError = e.asInstanceOf[StorageError.NotFound]
      assertEquals(notFoundError.entity, "Job")
      assertEquals(notFoundError.id, "job-456")
    }
  }

  test("wrapDbError converts SQLException to StorageError") {
    val sqlException = new java.sql.SQLException("Connection failed")

    val block: ApplicationContext ?=> String =
      throw sqlException

    val result = testComponent
      .wrapDbError("query")(block)
      .attempt
      .unsafeRunSync()

    assert(result.isLeft)
    result.left.foreach { e =>
      assert(e.isInstanceOf[StorageError.ConnectionFailed])
    }
  }

//  test("wrapDbError converts generic Exception to TransactionFailed") {
//    val genericException = new RuntimeException("Unexpected error")
//
//    val block: ApplicationContext ?=> String = throw genericException
//    val result = TestReflection.invokeArgs[IO[String]](testComponent, "wrapDbError", "update", block)
//      .attempt
//      .unsafeRunSync()
//
//    assert(result.isLeft)
//    result.left.foreach { e =>
//      assert(e.isInstanceOf[StorageError.TransactionFailed])
//    }
//  }
//
//  test("wrapDbError returns success value when no exception") {
//    val block: ApplicationContext ?=> String = {
//      given ctx: ApplicationContext = summon[ApplicationContext]
//      "success"
//    }
//    val result = TestReflection.invokeArgs[IO[String]](testComponent, "wrapDbError", "query", block)
//      .unsafeRunSync()
//
//    assertEquals(result, "success")
//  }

  test("multiple operations create separate contexts") {
    val ctx1 = getOperationCtx("save")
    val ctx2 = getOperationCtx("delete")

    assertEquals(ctx1.source, "save")
    assertEquals(ctx2.source, "delete")
  }

  test("contextualize preserves error details") {
    given ctx: ApplicationContext = getOperationCtx("update")

    val originalError = StorageError.DuplicateKey("Pipeline", "dup-id")
    val contextualized =
      TestReflection.invokeArgs[StorageError.DuplicateKey](testComponent, "contextualize", originalError, ctx)

    assertEquals(contextualized.entity, "Pipeline")
    assertEquals(contextualized.id, "dup-id")
    assert(contextualized.message.contains("dup-id"))
  }
