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
  type ContextBlock[A] = ApplicationContext ?=> A

  /** Base context for this component - created once */
  protected lazy val baseCtx: ApplicationContext = ApplicationContext(componentName)

  /** Scopes context to a specific operation (e.g., "findById", "save") */
  protected def withOperation[A](op: String)(block: ContextBlock[A]): A =
    given ctx: ApplicationContext = baseCtx.child(op)
    block

  /** Scopes context to a specific operation (e.g., "findById", "save") */
  protected def withOperation(op: String): ApplicationContext = baseCtx.child(op)

  /** Enriches any StorageError with this component's context */
  protected def contextualize[E <: StorageError](error: E)(using ctx: ApplicationContext): E =
    ctx.attach(error).asInstanceOf[E]

  /** Helper for raising a contextualized error in IO */
  protected def fail[A](error: StorageError)(using ctx: ApplicationContext): IO[A] =
    IO.raiseError(contextualize(error))

  protected def withDefaultContext[A](block: ContextBlock[A]): A =
    given ctx: ApplicationContext = baseCtx
    block

  /** Wraps a block that might throw, converting to StorageError */
  protected[context] def wrapDbError[A](operation: String)(block: ContextBlock[A]): IO[A] =
    withOperation(operation) {
      IO.defer(IO(block)).handleErrorWith {
        case e: java.sql.SQLException =>
          fail(StorageError.ConnectionFailed(e))
        case e: Exception =>
          fail(StorageError.TransactionFailed(e))
      }
    }
