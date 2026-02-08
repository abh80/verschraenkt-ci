package com.verschraenkt.ci.storage.fixtures

import cats.effect.{ IO, Ref, Resource }
import cats.syntax.all.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.verschraenkt.ci.storage.config.DatabaseConfig
import com.verschraenkt.ci.storage.db.DatabaseModule
import org.slf4j.LoggerFactory
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.*

/** Testcontainers fixture for PostgreSQL integration tests
  *
  * Provides a reusable PostgreSQL container and database configuration for integration tests.
  */
trait DatabaseContainerFixture extends TestDatabaseFixture:
  private val logger               = LoggerFactory.getLogger(getClass)
  private var isSharing: Boolean   = false
  private var autoMigrate: Boolean = false
  private var hasMigrated: Boolean = false
  private lazy val containerCache: Ref[IO, Option[PostgreSQLContainer]] =
    Ref.unsafe[IO, Option[PostgreSQLContainer]](None)

  /** Instruct if the class is willing to share container for faster tests */
  def sharing(sharing: Boolean): Unit = isSharing = sharing

  /** Quick shortcut to share */
  def sharing(): Unit =
    isSharing = true

  /** Configure whether to automatically run migrations when creating database module
    *
    * @param enabled
    *   If true, migrations will be run automatically when withDatabase is called. Default is false, requiring
    *   explicit use of withMigratedDatabase.
    */
  def autoMigrate(enabled: Boolean): Unit = autoMigrate = enabled

  /** PostgreSQL container as a Resource with memoization support */
  def postgresContainerResource(): Resource[IO, PostgreSQLContainer] =
    if isSharing then
      // Memoized resource: start once, reuse across all tests
      Resource.eval(
        containerCache.get.flatMap {
          case Some(container) =>
            IO.pure(container)
          case None =>
            IO {
              val container = PostgreSQLContainer
                .Def(
                  dockerImageName = DockerImageName.parse("postgres:16-alpine"),
                  databaseName = "verschraenkt_ci_test",
                  username = "test",
                  password = "test"
                )
                .createContainer()
              container.start()
              container
            }.flatTap(container => containerCache.set(Some(container)))
        }
      )
    else
      // Non-shared resource: create new container each time
      Resource.make(
        IO {
          val container = PostgreSQLContainer
            .Def(
              dockerImageName = DockerImageName.parse("postgres:16-alpine"),
              databaseName = "verschraenkt_ci_test",
              username = "test",
              password = "test"
            )
            .createContainer()
          container.start()
          container
        }
      )(container => IO(container.stop()))

  /** Run a test with a database connection (shared if sharing is enabled)
    *
    * If autoMigrate is enabled, migrations will be run automatically before the test. Otherwise, you should
    * call withMigratedDatabase explicitly to run migrations.
    *
    * Example usage:
    * {{{
    * // Option 1: Explicit migration control (default)
    * withDatabase { dbModule =>
    *   withMigratedDatabase(dbModule) { db =>
    *     // test code
    *   }
    * }
    *
    * // Option 2: Auto-migrations
    * autoMigrate(true)
    * withDatabase { dbModule =>
    *   // test code - migrations already applied
    * }
    * }}}
    */
  def withDatabase[A](test: DatabaseModule => IO[A]): IO[A] =
    IO(logger.info(s"withDatabase called (autoMigrate=$autoMigrate, sharing=$isSharing)")) *>
      postgresContainerResource()
        .flatMap(createDatabaseModule)
        .use { dbModule =>
          IO(logger.info("DatabaseModule resource acquired")) *>
            (if autoMigrate && !hasMigrated then
               IO(logger.info("Auto-migrate enabled, running migrations before test")) *>
                 runMigrations(dbModule).flatMap { _ =>
                   IO(logger.info("Migrations complete, starting test")) *>
                     IO.delay { hasMigrated = true } *>
                     test(dbModule)
                 }
             else
               IO(logger.info("Auto-migrate disabled, running test directly")) *>
                 test(dbModule)
            )
              .guarantee(IO(logger.info("Test completed, DatabaseModule will be released")))
        } <*
      IO(logger.info("DatabaseModule resource released"))

  /** Ensures a fresh container is always made */
  def withNewDatabase[A](test: DatabaseModule => IO[A]): IO[A] =
    val currentSharing = isSharing
    try
      isSharing = false
      postgresContainerResource()
        .flatMap(createDatabaseModule)
        .use { dbModule =>
          if autoMigrate then runMigrations(dbModule).flatMap(_ => test(dbModule))
          else test(dbModule)
        }
    finally isSharing = currentSharing

  /** Get database configuration from the running container */
  def getDatabaseConfig(container: PostgreSQLContainer): DatabaseConfig =
    DatabaseConfig(
      url = container.jdbcUrl,
      user = container.username,
      password = container.password,
      driver = container.driverClassName,
      poolConfig = com.verschraenkt.ci.storage.config.HikariConfig(
        maxPoolSize = 5,
        minIdle = 1,
        connectionTimeout = 10.seconds,
        idleTimeout = 5.minutes,
        maxLifetime = 10.minutes,
        leakDetectionThreshold = 0.seconds
      ),
      postgresConfig = com.verschraenkt.ci.storage.config.PostgresConfig(
        schema = "public",
        sslMode = "disable",
        applicationName = "verschraenkt-ci-test",
        prepareThreshold = 5
      )
    )

  /** Create a DatabaseModule resource from the container */
  def createDatabaseModule(container: PostgreSQLContainer): Resource[IO, DatabaseModule] =
    Resource.eval(IO(logger.info(s"Creating DatabaseModule for container at ${container.jdbcUrl}"))) *>
      DatabaseModule
        .resource(getDatabaseConfig(container))
        .evalTap(_ => IO(logger.info("DatabaseModule created successfully")))
