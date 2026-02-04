package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.storage.db.DatabaseModule

/** Trait providing database operation helpers for repositories
  *
  * Mix this into repository classes to get convenient methods for running Slick actions within IO.
  */
trait DatabaseOperations:
  protected def dbModule: DatabaseModule

  protected val profile = com.verschraenkt.ci.storage.db.PostgresProfile
  import profile.api.*

  /** Run a database action and lift the result into IO */
  protected def run[R](action: DBIO[R]): IO[R] =
    IO.fromFuture(IO(dbModule.database.run(action)))

  /** Run a database action within a transaction */
  protected def transactionally[R](action: DBIO[R]): IO[R] =
    run(action.transactionally)
