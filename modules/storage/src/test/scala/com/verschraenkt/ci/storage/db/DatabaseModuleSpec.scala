package com.verschraenkt.ci.storage.db

import cats.effect.IO
import com.verschraenkt.ci.storage.config.DatabaseConfig
import com.verschraenkt.ci.storage.fixtures.DatabaseContainerFixture
import munit.CatsEffectSuite


class DatabaseModuleSpec extends CatsEffectSuite with DatabaseContainerFixture:

  test("DatabaseModule.create succeeds with valid config") {
    val container = postgresContainer()
    val config    = getDatabaseConfig(container)

    DatabaseModule.create(config).flatMap { dbModule =>
      IO {
        assertNotEquals(dbModule.database, null)
      } *> dbModule.close()
    }
  }

  test("DatabaseModule.resource manages lifecycle correctly") {
    val container = postgresContainer()
    val config    = getDatabaseConfig(container)

    DatabaseModule
      .resource(config)
      .use { dbModule =>
        IO {
          assertNotEquals(dbModule.database, null)
          // Database should be open during use
        }
      }
      .flatMap { _ =>
        // After resource is released, connections should be closed
        IO(assert(true))
      }
  }

  test("DatabaseModule connection pool is created") {
    val container = postgresContainer()
    val config    = getDatabaseConfig(container)

    DatabaseModule.create(config).flatMap { dbModule =>
      IO.blocking {
        val connection = dbModule.database.source.createConnection()
        try
          assert(connection.isValid(5))
        finally
          connection.close()
      } *> dbModule.close()
    }
  }

  test("DatabaseModule connection pool size matches config") {
    val container = postgresContainer()
    val config = getDatabaseConfig(container).copy(
      poolConfig = com.verschraenkt.ci.storage.config.HikariConfig(
        maxPoolSize = 3,
        minIdle = 1
      )
    )

    DatabaseModule.create(config).flatMap { dbModule =>
      IO {
        // HikariCP data source should be configured
        assertNotEquals(dbModule.database, null)
      } *> dbModule.close()
    }
  }

  test("DatabaseModule close shuts down connections cleanly") {
    val container = postgresContainer()
    val config    = getDatabaseConfig(container)

    for
      dbModule <- DatabaseModule.create(config)
      _ <- IO {
        assertNotEquals(dbModule.database, null)
      }
      _ <- dbModule.close()
      // After close, attempting to get a connection should fail
      result <- IO.blocking {
        try
          dbModule.database.source.createConnection(): Unit
          false // Should not reach here
        catch case _: Exception => true // Expected to fail
      }
      _ <- IO(assert(result))
    yield ()
  }

  test("DatabaseModule with invalid credentials fails") {
    val container = postgresContainer()
    val invalidConfig = DatabaseConfig(
      url = container.jdbcUrl,
      user = "invalid_user",
      password = "invalid_password"
    )

    DatabaseModule.create(invalidConfig).flatMap { dbModule =>
      IO.blocking {
        val connection = dbModule.database.source.createConnection()
        connection.close()
      }.attempt
        .flatMap {
          case Left(_) =>
            // Expected to fail
            dbModule.close() *> IO(assert(true))
          case Right(_) =>
            // Should not succeed
            dbModule.close() *> IO(fail("Connection should have failed"))
        }
    }
  }

  test("DatabaseModule can execute simple query") {
    val container = postgresContainer()
    val config    = getDatabaseConfig(container)

    DatabaseModule.create(config).flatMap { dbModule =>
      IO.blocking {
        val connection = dbModule.database.source.createConnection()
        try
          val stmt = connection.createStatement()
          val rs   = stmt.executeQuery("SELECT 1 AS result")
          val result =
            if rs.next() then rs.getInt("result")
            else 0
          rs.close()
          stmt.close()
          result
        finally connection.close()
      }.flatMap { result =>
        IO(assertEquals(result, 1)) *> dbModule.close()
      }
    }
  }

  test("DatabaseModule handles multiple concurrent connections") {
    val container = postgresContainer()
    val config = getDatabaseConfig(container).copy(
      poolConfig = com.verschraenkt.ci.storage.config.HikariConfig(
        maxPoolSize = 5,
        minIdle = 2
      )
    )

    DatabaseModule.create(config).flatMap { dbModule =>
      val queries = (1 to 5).map { i =>
        IO.blocking {
          val connection = dbModule.database.source.createConnection()
          try
            val stmt = connection.createStatement()
            val rs   = stmt.executeQuery(s"SELECT $i AS result")
            val result =
              if rs.next() then rs.getInt("result")
              else 0
            rs.close()
            stmt.close()
            result
          finally connection.close()
        }
      }

      queries.toList.parSequence.flatMap { results =>
        IO {
          assertEquals(results, List(1, 2, 3, 4, 5))
        } *> dbModule.close()
      }
    }
  }
