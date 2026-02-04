package com.verschraenkt.ci.storage.db

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory
import com.verschraenkt.ci.storage.config.DatabaseConfig
import com.zaxxer.hikari.{HikariConfig as HConfig, HikariDataSource}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.*

/** Database module providing connection pool and database instance
  *
  * This module manages the lifecycle of the database connection pool using HikariCP and provides a Slick database
  * instance for executing queries.
  */
final class DatabaseModule private (
    val database: Database,
    private val dataSource: HikariDataSource
):
  /** Close the database connection pool */
  def close(): IO[Unit] =
    IO.blocking {
      database.close()
      dataSource.close()
    }

object DatabaseModule:
  /** Create a DatabaseModule as a Resource for automatic cleanup
    *
    * @param config
    *   Database configuration
    * @return
    *   Resource that manages database lifecycle
    */
  def resource(config: DatabaseConfig): Resource[IO, DatabaseModule] =
    Resource.make(create(config))(_.close())

  /** Create a DatabaseModule
    *
    * @param config
    *   Database configuration
    * @return
    *   IO containing the database module
    */
  def create(config: DatabaseConfig): IO[DatabaseModule] =
    IO.blocking {
      val hikariConfig = new HConfig()

      // Basic connection settings
      hikariConfig.setJdbcUrl(config.url)
      hikariConfig.setUsername(config.user)
      hikariConfig.setPassword(config.password)
      hikariConfig.setDriverClassName(config.driver)

      // Pool settings
      hikariConfig.setMaximumPoolSize(config.poolConfig.maxPoolSize)
      hikariConfig.setMinimumIdle(config.poolConfig.minIdle)
      hikariConfig.setConnectionTimeout(config.poolConfig.connectionTimeout.toMillis)
      hikariConfig.setIdleTimeout(config.poolConfig.idleTimeout.toMillis)
      hikariConfig.setMaxLifetime(config.poolConfig.maxLifetime.toMillis)

      if config.poolConfig.leakDetectionThreshold > 0.seconds then
        hikariConfig.setLeakDetectionThreshold(config.poolConfig.leakDetectionThreshold.toMillis)

      // PostgreSQL-specific settings
      hikariConfig.addDataSourceProperty("ApplicationName", config.postgresConfig.applicationName)
      hikariConfig.addDataSourceProperty("currentSchema", config.postgresConfig.schema)
      hikariConfig.addDataSourceProperty("sslmode", config.postgresConfig.sslMode)
      hikariConfig.addDataSourceProperty("prepareThreshold", config.postgresConfig.prepareThreshold.toString)

      // Performance optimizations
      hikariConfig.addDataSourceProperty("cachePrepStmts", "true")
      hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250")
      hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
      hikariConfig.addDataSourceProperty("useServerPrepStmts", "true")

      // Create data source and database
      val dataSource = new HikariDataSource(hikariConfig)
      val database   = Database.forDataSource(dataSource, maxConnections = Some(config.poolConfig.maxPoolSize))

      new DatabaseModule(database, dataSource)
    }

  /** Create from Typesafe Config
    *
    * Expected configuration structure:
    * {{{
    * database {
    *   url = "jdbc:postgresql://localhost:5432/verschraenkt_ci"
    *   user = "postgres"
    *   password = "postgres"
    *   driver = "org.postgresql.Driver"
    *   pool {
    *     maxPoolSize = 10
    *     minIdle = 2
    *     connectionTimeout = 30s
    *     idleTimeout = 10m
    *     maxLifetime = 30m
    *     leakDetectionThreshold = 0s
    *   }
    *   postgres {
    *     schema = "public"
    *     sslMode = "prefer"
    *     applicationName = "verschraenkt-ci"
    *     prepareThreshold = 5
    *   }
    * }
    * }}}
    */
  def fromConfig(configPath: String = "database"): Resource[IO, DatabaseModule] =
    Resource.eval(IO.blocking {
      val config     = ConfigFactory.load().getConfig(configPath)
      val poolConfig = config.getConfig("pool")
      val pgConfig   = config.getConfig("postgres")

      import scala.concurrent.duration.*

      DatabaseConfig(
        url = config.getString("url"),
        user = config.getString("user"),
        password = config.getString("password"),
        driver = if config.hasPath("driver") then config.getString("driver") else "org.postgresql.Driver",
        poolConfig = com.verschraenkt.ci.storage.config.HikariConfig(
          maxPoolSize = poolConfig.getInt("maxPoolSize"),
          minIdle = poolConfig.getInt("minIdle"),
          connectionTimeout = Duration(poolConfig.getDuration("connectionTimeout").toMillis, MILLISECONDS),
          idleTimeout = Duration(poolConfig.getDuration("idleTimeout").toMillis, MILLISECONDS),
          maxLifetime = Duration(poolConfig.getDuration("maxLifetime").toMillis, MILLISECONDS),
          leakDetectionThreshold = Duration(poolConfig.getDuration("leakDetectionThreshold").toMillis, MILLISECONDS)
        ),
        postgresConfig = com.verschraenkt.ci.storage.config.PostgresConfig(
          schema = pgConfig.getString("schema"),
          sslMode = pgConfig.getString("sslMode"),
          applicationName = pgConfig.getString("applicationName"),
          prepareThreshold = pgConfig.getInt("prepareThreshold")
        )
      )
    }).flatMap(resource)
