package com.verschraenkt.ci.engine.api

import munit.FunSuite

class SnowflakeSpec extends FunSuite:

  test("fromLong rejects negative values") {
    val result = Snowflake.fromLong(-1L)
    assert(result.isLeft)
  }

  test("toLong/fromLong round-trip preserves fields") {
    val machineId = 42
    val sequence  = 7
    val ts        = Snowflake.Epoch + 1234L

    val raw  = Snowflake.toLong(machineId, sequence, ts)
    val from = Snowflake.fromLong(raw).toOption.getOrElse(fail("expected Right"))

    assertEquals(from.machineId, machineId)
    assertEquals(from.sequence, sequence)
    assertEquals(from.timestamp, ts)
    assertEquals(from.value, raw)
  }

  test("toLong rejects out-of-range machineId and sequence") {
    val ts = Snowflake.Epoch + 1L

    intercept[IllegalArgumentException] {
      Snowflake.toLong(Snowflake.MaxMachineId + 1, 0, ts)
    }: Unit

    intercept[IllegalArgumentException] {
      Snowflake.toLong(0, Snowflake.MaxSequence + 1, ts)
    }
  }

  test("toLong rejects timestamps before epoch") {
    intercept[IllegalArgumentException] {
      Snowflake.toLong(0, 0, Snowflake.Epoch - 1L)
    }
  }
