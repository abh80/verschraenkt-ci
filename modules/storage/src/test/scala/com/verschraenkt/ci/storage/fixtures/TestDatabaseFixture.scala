package com.verschraenkt.ci.storage.fixtures

import cats.effect.{ IO, Resource }
import cats.data.NonEmptyList
import com.verschraenkt.ci.storage.db.DatabaseModule
import fly4s.Fly4s
import fly4s.data.*
import org.flywaydb.core.api.output.MigrateErrorResult
import org.slf4j.LoggerFactory

/** Test utilities for database schema setup and cleanup */
trait TestDatabaseFixture:
  private val logger = LoggerFactory.getLogger(getClass)

  private def createFlyway(dbModule: DatabaseModule): Resource[IO, Fly4s[IO]] =
    Resource
      .eval(IO.blocking {
        val connection = dbModule.database.source.createConnection()
        val url        = connection.getMetaData.getURL
        val user       = connection.getMetaData.getUserName
        connection.close()
        logger.info(s"Flyway connecting to: $url (user: $user)")
        (url, user)
      })
      .flatMap { case (url, user) =>
        Fly4s.make[IO](
          url = url,
          user = Some(user),
          password = Some("test").map(_.toCharArray),
          config = Fly4sConfig(
            table = "flyway_schema_history",
            locations = Locations("classpath:db/migration"),
            connectRetries = 0,
            schemaNames = Some(NonEmptyList.of("public")),
            createSchemas = true,
            baselineOnMigrate = true,
            cleanDisabled = false,
            group = false,
            mixed = false,
            outOfOrder = false,
            skipDefaultCallbacks = false,
            skipDefaultResolvers = false,
            validateMigrationNaming = true,
            validateOnMigrate = true,
            placeholderReplacement = false
          )
        )
      }

  /** Run database migrations using Flyway */
  def runMigrations(dbModule: DatabaseModule): IO[Unit] =
    logger.info("Starting database migrations...")
    createFlyway(dbModule).use { fly4s =>
      fly4s.migrate.flatMap {
        case error: MigrateErrorResult =>
          val errorMsg = s"Migration failed: ${error.error.message()}"
          logger.error(errorMsg)
          IO.raiseError(new RuntimeException(errorMsg))

        case success =>
          logger.info(s"Migrations completed successfully: ${success.migrationsExecuted} migrations executed")
          IO.unit
      }
    }

  /** Clean all tables in the database using Flyway clean */
  def cleanDatabase(dbModule: DatabaseModule): IO[Unit] =
    logger.info("Cleaning database...")
    createFlyway(dbModule)
      .use { fly4s =>
        fly4s.clean.void
      }
      .flatTap(_ => IO(logger.info("Database cleaned successfully")))

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
