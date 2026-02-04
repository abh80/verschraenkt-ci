package com.verschraenkt.ci.storage.config

import scala.concurrent.duration.*

/** Database connection configuration
  *
  * @param url
  *   JDBC connection URL (e.g., jdbc:postgresql://localhost:5432/verschraenkt_ci)
  * @param user
  *   Database username
  * @param password
  *   Database password
  * @param driver
  *   JDBC driver class name
  * @param poolConfig
  *   Connection pool configuration
  * @param postgresConfig
  *   PostgreSQL-specific settings
  */
final case class DatabaseConfig(
    url: String,
    user: String,
    password: String,
    driver: String = "org.postgresql.Driver",
    poolConfig: HikariConfig = HikariConfig(),
    postgresConfig: PostgresConfig = PostgresConfig()
)

/** HikariCP connection pool configuration
  *
  * @param maxPoolSize
  *   Maximum number of connections in the pool
  * @param minIdle
  *   Minimum number of idle connections to maintain
  * @param connectionTimeout
  *   Maximum time to wait for a connection from the pool
  * @param idleTimeout
  *   Maximum time a connection can sit idle in the pool
  * @param maxLifetime
  *   Maximum lifetime of a connection in the pool
  * @param leakDetectionThreshold
  *   Time before a connection is considered leaked (0 = disabled)
  */
final case class HikariConfig(
    maxPoolSize: Int = 10,
    minIdle: Int = 2,
    connectionTimeout: FiniteDuration = 30.seconds,
    idleTimeout: FiniteDuration = 10.minutes,
    maxLifetime: FiniteDuration = 30.minutes,
    leakDetectionThreshold: FiniteDuration = 0.seconds
)

/** PostgreSQL-specific configuration
  *
  * @param schema
  *   Default schema to use
  * @param sslMode
  *   SSL mode (disable, allow, prefer, require, verify-ca, verify-full)
  * @param applicationName
  *   Application name for connection tracking
  * @param prepareThreshold
  *   Number of executions before a statement is prepared (-1 = always prepare)
  */
final case class PostgresConfig(
    schema: String = "public",
    sslMode: String = "prefer",
    applicationName: String = "verschraenkt-ci",
    prepareThreshold: Int = 5
)

object DatabaseConfig:
  /** Development configuration with sensible defaults */
  def development(
      host: String = "localhost",
      port: Int = 5432,
      database: String = "verschraenkt_ci",
      user: String = "postgres",
      password: String = "postgres"
  ): DatabaseConfig =
    DatabaseConfig(
      url = s"jdbc:postgresql://$host:$port/$database",
      user = user,
      password = password,
      poolConfig = HikariConfig(
        maxPoolSize = 5,
        minIdle = 1,
        connectionTimeout = 10.seconds,
        leakDetectionThreshold = 60.seconds
      )
    )

  /** Production configuration with stricter settings */
  def production(
      url: String,
      user: String,
      password: String
  ): DatabaseConfig =
    DatabaseConfig(
      url = url,
      user = user,
      password = password,
      poolConfig = HikariConfig(
        maxPoolSize = 20,
        minIdle = 5,
        connectionTimeout = 30.seconds,
        idleTimeout = 10.minutes,
        maxLifetime = 30.minutes,
        leakDetectionThreshold = 120.seconds
      ),
      postgresConfig = PostgresConfig(
        sslMode = "require"
      )
    )
