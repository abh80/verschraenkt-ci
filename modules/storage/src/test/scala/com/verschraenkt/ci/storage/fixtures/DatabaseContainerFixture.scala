package com.verschraenkt.ci.storage.fixtures

import cats.effect.{ IO, Resource }
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.fixtures.TestContainersFixtures
import com.verschraenkt.ci.storage.config.DatabaseConfig
import com.verschraenkt.ci.storage.db.DatabaseModule
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.*

/** Testcontainers fixture for PostgreSQL integration tests
  *
  * Provides a reusable PostgreSQL container and database configuration for integration tests.
  */
trait DatabaseContainerFixture extends CatsEffectSuite with TestContainersFixtures:

  /** PostgreSQL container fixture */
  def postgresContainer: Fixture[PostgreSQLContainer] = ForEachContainerFixture(
    PostgreSQLContainer
      .Def(
        dockerImageName = DockerImageName.parse("postgres:16-alpine"),
        databaseName = "verschraenkt_ci_test",
        username = "test",
        password = "test"
      )
      .createContainer()
  )

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

  /** Run a test with a fresh database connection */
  def withDatabase[A](test: DatabaseModule => IO[A]): IO[A] =
    createDatabaseModule(postgresContainer()).use(test)
