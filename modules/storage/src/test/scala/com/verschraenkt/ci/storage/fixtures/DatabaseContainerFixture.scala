package com.verschraenkt.ci.storage.fixtures

import cats.effect.{ IO, Ref, Resource }
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.verschraenkt.ci.storage.config.DatabaseConfig
import com.verschraenkt.ci.storage.db.DatabaseModule
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.*

/** Testcontainers fixture for PostgreSQL integration tests
  *
  * Provides a reusable PostgreSQL container and database configuration for integration tests.
  */
trait DatabaseContainerFixture:
  private var isSharing: Boolean = false
  private lazy val containerCache: Ref[IO, Option[PostgreSQLContainer]] =
    Ref.unsafe[IO, Option[PostgreSQLContainer]](None)

  /** Instruct if the class is willing to share container for faster tests */
  def sharing(sharing: Boolean): Unit = isSharing = sharing

  /** Quick shortcut to share */
  def sharing(): Unit =
    isSharing = true

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

  /** Run a test with a database connection (shared if sharing is enabled) */
  def withDatabase[A](test: DatabaseModule => IO[A]): IO[A] =
    postgresContainerResource().flatMap(createDatabaseModule).use(test)

  /** Ensures a fresh container is always made */
  def withNewDatabase[A](test: DatabaseModule => IO[A]): IO[A] =
    val currentSharing = isSharing
    try
      isSharing = false
      postgresContainerResource().flatMap(createDatabaseModule).use(test)
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
    val config = getDatabaseConfig(container)
    DatabaseModule.resource(config)
