package com.verschraenkt.ci.storage.fixtures

import cats.effect.IO
import com.verschraenkt.ci.storage.db.DatabaseModule

import scala.io.Source

/** Test utilities for database schema setup and cleanup
  *
  * Provides helpers for running migrations and cleaning the database between tests.
  */
trait TestDatabaseFixture:

  /** Run database migrations from the migration SQL file */
  def runMigrations(dbModule: DatabaseModule): IO[Unit] =
    IO.blocking {
      val migrationSql = Source
        .fromResource("db/migration/1.sql")
        .getLines()
        .mkString("\n")

      // Split by semicolons and execute each statement
      val statements = migrationSql
        .split(";")
        .map(_.trim)
        .filter(_.nonEmpty)

      val connection = dbModule.database.source.createConnection()
      try
        val stmt = connection.createStatement()
        try
          statements.foreach { sql =>
            if sql.nonEmpty then
              stmt.execute(sql): Unit // Ignore lint errors due to discarding of boolean value
          }
        finally
          stmt.close()
      finally connection.close()
    }

  /** Clean all tables in the database (for test isolation) */
  def cleanDatabase(dbModule: DatabaseModule): IO[Unit] =
    IO.blocking {
      val connection = dbModule.database.source.createConnection()
      try
        val stmt = connection.createStatement()
        try
          // Disable foreign key checks temporarily
          stmt.execute("SET session_replication_role = 'replica'")

          // Get all table names
          val rs = stmt.executeQuery(
            """
            SELECT tablename 
            FROM pg_tables 
            WHERE schemaname = 'public'
            """
          )

          val tables = scala.collection.mutable.ListBuffer[String]()
          while rs.next() do tables += rs.getString("tablename")
          rs.close()

          // Truncate all tables
          tables.foreach { table =>
            stmt.execute(s"TRUNCATE TABLE $table CASCADE")
          }

          // Re-enable foreign key checks
          stmt.execute("SET session_replication_role = 'origin'"): Unit
        finally stmt.close()
      finally connection.close()
    }

  /** Run a test with a clean database (migrations applied, then cleaned after test) */
  def withCleanDatabase[A](dbModule: DatabaseModule)(test: => IO[A]): IO[A] =
    for
      _      <- runMigrations(dbModule)
      result <- test
      _      <- cleanDatabase(dbModule)
    yield result

  /** Run migrations once and provide the database module to the test */
  def withMigratedDatabase[A](dbModule: DatabaseModule)(test: DatabaseModule => IO[A]): IO[A] =
    runMigrations(dbModule) *> test(dbModule)
