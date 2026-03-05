package com.verschraenkt.ci.engine.core

import com.verschraenkt.ci.engine.api.Snowflake
import munit.FunSuite

class AppConfigSpec extends FunSuite:

  private val propMachineId = "ci.snowflake.machineId"
  private val propBackward  = "ci.snowflake.maxClockBackwardMs"

  override def beforeEach(context: BeforeEach): Unit =
    clearProps()

  override def afterEach(context: AfterEach): Unit =
    clearProps()

  private def clearProps(): Unit =
    System.clearProperty(propMachineId): Unit
    System.clearProperty(propBackward): Unit

  private def invokeLoad(): Either[String, AppConfig] =
    val method = AppConfig.getClass.getDeclaredMethod("load")
    method.setAccessible(true)
    method.invoke(AppConfig).asInstanceOf[Either[String, AppConfig]]

  private def invokeStableDevMachineId(): Int =
    val method = AppConfig.getClass.getDeclaredMethod("stableDevMachineId")
    method.setAccessible(true)
    method.invoke(AppConfig).asInstanceOf[Int]

  test("AppEnv.fromString parses known values") {
    assertEquals(AppEnv.fromString("dev"), Some(AppEnv.Development))
    assertEquals(AppEnv.fromString(" Development "), Some(AppEnv.Development))
    assertEquals(AppEnv.fromString("TEST"), Some(AppEnv.Test))
    assertEquals(AppEnv.fromString("prod"), Some(AppEnv.Production))
    assertEquals(AppEnv.fromString("Production"), Some(AppEnv.Production))
    assertEquals(AppEnv.fromString("unknown"), None)
  }

  test("load uses system property machineId and defaults maxClockBackwardMs") {
    assume(!sys.env.contains("CI_SNOWFLAKE_MACHINE_ID"), "env machineId would mask property")
    assume(
      !sys.env.contains("CI_SNOWFLAKE_MAX_CLOCK_BACKWARD_MS"),
      "env maxClockBackwardMs would mask property"
    )

    System.setProperty(propMachineId, "12")

    val result = invokeLoad()
    val cfg = result match
      case Right(value) => value
      case Left(msg)    => fail(msg)

    assertEquals(cfg.snowflake.machineId, 12)
    assertEquals(cfg.snowflake.maxClockBackwardMs, 5L)
  }

  test("load rejects machineId outside allowed range") {
    assume(!sys.env.contains("CI_SNOWFLAKE_MACHINE_ID"), "env machineId would mask property")

    System.setProperty(propMachineId, (Snowflake.MaxMachineId + 1).toString)

    val result = invokeLoad()
    result match
      case Right(value) => fail(s"Expected failure but got ${value.toString}")
      case Left(msg) =>
        assert(msg.contains("machineId must be"))
        assert(msg.contains(Snowflake.MaxMachineId.toString))
  }

  test("load rejects negative maxClockBackwardMs") {
    assume(!sys.env.contains("CI_SNOWFLAKE_MACHINE_ID"), "env machineId would mask property")
    assume(
      !sys.env.contains("CI_SNOWFLAKE_MAX_CLOCK_BACKWARD_MS"),
      "env maxClockBackwardMs would mask property"
    )

    System.setProperty(propMachineId, "1")
    System.setProperty(propBackward, "-1")

    val result = invokeLoad()
    result match
      case Right(value) => fail(s"Expected failure but got ${value.toString}")
      case Left(msg)    => assert(msg.contains("maxClockBackwardMs must be >= 0"))
  }

  test("load uses stableDevMachineId when machineId is missing in dev") {
    assume(!sys.env.contains("CI_SNOWFLAKE_MACHINE_ID"), "env machineId would mask fallback")
    assume(!sys.env.contains("APP_ENV"), "env app env would affect default")

    val expected = invokeStableDevMachineId()

    val result = invokeLoad()
    val cfg = result match
      case Right(value) => value
      case Left(msg)    => fail(msg)

    assertEquals(cfg.env, AppEnv.Development)
    assertEquals(cfg.snowflake.machineId, expected)
  }
