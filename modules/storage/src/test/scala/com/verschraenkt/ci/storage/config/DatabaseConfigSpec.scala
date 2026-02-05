package com.verschraenkt.ci.storage.config

import munit.FunSuite

import scala.concurrent.duration.*

class DatabaseConfigSpec extends FunSuite:

  test("DatabaseConfig.development creates correct configuration") {
    val config = DatabaseConfig.development()

    assertEquals(config.url, "jdbc:postgresql://localhost:5432/verschraenkt_ci")
    assertEquals(config.user, "postgres")
    assertEquals(config.password, "postgres")
    assertEquals(config.driver, "org.postgresql.Driver")
    assertEquals(config.poolConfig.maxPoolSize, 5)
    assertEquals(config.poolConfig.minIdle, 1)
    assertEquals(config.poolConfig.connectionTimeout, 10.seconds)
    assertEquals(config.poolConfig.leakDetectionThreshold, 60.seconds)
  }

  test("DatabaseConfig.development accepts custom parameters") {
    val config = DatabaseConfig.development(
      host = "db.example.com",
      port = 5433,
      database = "custom_db",
      user = "admin",
      password = "secret"
    )

    assertEquals(config.url, "jdbc:postgresql://db.example.com:5433/custom_db")
    assertEquals(config.user, "admin")
    assertEquals(config.password, "secret")
  }

  test("DatabaseConfig.production creates correct configuration") {
    val config = DatabaseConfig.production(
      url = "jdbc:postgresql://prod-db:5432/verschraenkt_ci",
      user = "prod_user",
      password = "prod_pass"
    )

    assertEquals(config.url, "jdbc:postgresql://prod-db:5432/verschraenkt_ci")
    assertEquals(config.user, "prod_user")
    assertEquals(config.password, "prod_pass")
    assertEquals(config.driver, "org.postgresql.Driver")
    assertEquals(config.poolConfig.maxPoolSize, 20)
    assertEquals(config.poolConfig.minIdle, 5)
    assertEquals(config.poolConfig.connectionTimeout, 30.seconds)
    assertEquals(config.poolConfig.idleTimeout, 10.minutes)
    assertEquals(config.poolConfig.maxLifetime, 30.minutes)
    assertEquals(config.poolConfig.leakDetectionThreshold, 120.seconds)
    assertEquals(config.postgresConfig.sslMode, "require")
  }

  test("DatabaseConfig with custom parameters") {
    val customPool = HikariConfig(
      maxPoolSize = 15,
      minIdle = 3,
      connectionTimeout = 20.seconds,
      idleTimeout = 15.minutes,
      maxLifetime = 45.minutes,
      leakDetectionThreshold = 30.seconds
    )

    val customPostgres = PostgresConfig(
      schema = "custom_schema",
      sslMode = "verify-full",
      applicationName = "my-app",
      prepareThreshold = 10
    )

    val config = DatabaseConfig(
      url = "jdbc:postgresql://localhost:5432/mydb",
      user = "myuser",
      password = "mypass",
      driver = "org.postgresql.Driver",
      poolConfig = customPool,
      postgresConfig = customPostgres
    )

    assertEquals(config.poolConfig.maxPoolSize, 15)
    assertEquals(config.poolConfig.minIdle, 3)
    assertEquals(config.poolConfig.connectionTimeout, 20.seconds)
    assertEquals(config.postgresConfig.schema, "custom_schema")
    assertEquals(config.postgresConfig.sslMode, "verify-full")
    assertEquals(config.postgresConfig.applicationName, "my-app")
    assertEquals(config.postgresConfig.prepareThreshold, 10)
  }

  test("HikariConfig default values") {
    val config = HikariConfig()

    assertEquals(config.maxPoolSize, 10)
    assertEquals(config.minIdle, 2)
    assertEquals(config.connectionTimeout, 30.seconds)
    assertEquals(config.idleTimeout, 10.minutes)
    assertEquals(config.maxLifetime, 30.minutes)
    assertEquals(config.leakDetectionThreshold, 0.seconds)
  }

  test("PostgresConfig default values") {
    val config = PostgresConfig()

    assertEquals(config.schema, "public")
    assertEquals(config.sslMode, "prefer")
    assertEquals(config.applicationName, "verschraenkt-ci")
    assertEquals(config.prepareThreshold, 5)
  }

  test("DatabaseConfig with default driver") {
    val config = DatabaseConfig(
      url = "jdbc:postgresql://localhost:5432/test",
      user = "test",
      password = "test"
    )

    assertEquals(config.driver, "org.postgresql.Driver")
  }

  test("HikariConfig with various pool sizes") {
    val smallPool = HikariConfig(maxPoolSize = 2, minIdle = 1)
    assertEquals(smallPool.maxPoolSize, 2)
    assertEquals(smallPool.minIdle, 1)

    val largePool = HikariConfig(maxPoolSize = 50, minIdle = 10)
    assertEquals(largePool.maxPoolSize, 50)
    assertEquals(largePool.minIdle, 10)
  }

  test("PostgresConfig with different SSL modes") {
    val sslModes = List("disable", "allow", "prefer", "require", "verify-ca", "verify-full")

    sslModes.foreach { mode =>
      val config = PostgresConfig(sslMode = mode)
      assertEquals(config.sslMode, mode)
    }
  }
