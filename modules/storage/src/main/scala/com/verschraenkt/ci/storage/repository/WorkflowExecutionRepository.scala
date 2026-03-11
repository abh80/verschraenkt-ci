package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.tables.{ WorkflowExecutionRow, WorkflowExecutionTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }
import com.verschraenkt.ci.storage.errors.StorageError
import com.verschraenkt.ci.storage.util.TableCast

sealed trait IWorkflowExecutionRepository:
  def create(exec: WorkflowExecutionRow): IO[Unit]

class WorkflowExecutionRepository(val dbModule: DatabaseModule)
    extends IWorkflowExecutionRepository
    with DatabaseOperations
    with StorageContext
    with TableCast[WorkflowExecutionRow]:
  import PostgresProfile.api.*

  override def create(exec: WorkflowExecutionRow): IO[Unit] =
    withContext("create") {
      WorkflowExecutionInsert.insert(exec)
    }

  override def table: TableQuery[WorkflowExecutionTable] = TableQuery[WorkflowExecutionTable]

  private object WorkflowExecutionInsert extends InsertActionRaw:

    protected def entityName = "WorkflowExecutionDB"

    protected def getId(entry: WorkflowExecutionRow): String = entry.executionId.toString

    protected def transactionally[T](action: DBIO[T]): IO[T] =
      WorkflowExecutionRepository.this.transactionally(action)

    protected def fail[T](error: StorageError)(using applicationContext: ApplicationContext): IO[T] =
      WorkflowExecutionRepository.this.fail(error)
