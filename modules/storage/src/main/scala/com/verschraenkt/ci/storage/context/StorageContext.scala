package com.verschraenkt.ci.storage.context

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.errors.StorageError

/** Trait that provides seamless context management for storage operations. Mix this into any repository class
  * to get automatic error enrichment.
  */
trait StorageContext:
  /** Define the component name */
  protected def componentName: String = this.getClass.getSimpleName

  /** Base context for this component - created once */
  protected lazy val baseCtx: ApplicationContext = ApplicationContext(componentName)

  /** Scopes context to a specific operation (e.g., "findById", "save") */
  protected def withOperation(operation: String): ApplicationContext =
    baseCtx.child(operation)

  /** Enriches any StorageError with this component's context */
  protected def contextualize[E <: StorageError](error: E)(using ctx: ApplicationContext): E =
    ctx.attach(error).asInstanceOf[E]

  /** Helper for raising a contextualized error in IO */
  protected def fail[A](error: StorageError)(using ctx: ApplicationContext): IO[A] =
    IO.raiseError(contextualize(error))

  /** Wraps a block that might throw, converting to StorageError */
  protected def wrapDbError[A](operation: String)(block: ApplicationContext ?=> A): IO[A] =
    given ctx: ApplicationContext = withOperation(operation)
    IO.defer(IO(block)).handleErrorWith {
      case e: java.sql.SQLException =>
        fail(StorageError.ConnectionFailed(e))
      case e: Exception =>
        fail(StorageError.TransactionFailed(e))
    }
