package com.verschraenkt.ci.engine.api

import munit.FunSuite

class SnowflakeProviderSpec extends FunSuite:

  private final class TestClock(times: Array[Long]):
    private var idx = 0
    def now(): Long =
      val t =
        if idx < times.length then times(idx)
        else times(times.length - 1)
      idx += 1
      t

  test("make rejects invalid machineId") {
    intercept[IllegalArgumentException] {
      SnowflakeProvider.make(Snowflake.MaxMachineId + 1)
    }
  }

  test("tryNextId increments sequence within same millisecond") {
    val t0       = Snowflake.Epoch + 1000L
    val t1       = t0 + 1L
    val clock    = TestClock(Array(t0, t1, t1, t1))
    val provider = SnowflakeProvider.make(3, clock = () => clock.now())

    val a = provider.tryNextId().toOption.getOrElse(fail("expected Right"))
    val b = provider.tryNextId().toOption.getOrElse(fail("expected Right"))
    val c = provider.tryNextId().toOption.getOrElse(fail("expected Right"))

    assertEquals(a.sequence, 0)
    assertEquals(b.sequence, 1)
    assertEquals(c.sequence, 2)
    assertEquals(a.timestamp, t1)
    assertEquals(b.timestamp, t1)
    assertEquals(c.timestamp, t1)
  }

  test("tryNextId returns SequenceExhausted when sequence is exhausted") {
    val t0    = Snowflake.Epoch + 2000L
    val t1    = t0 + 1L
    var first = true
    val provider = SnowflakeProvider.make(
      1,
      clock = () =>
        if first then
          first = false
          t0
        else t1
    )

    // first call sets seq = 0 at t1
    provider.tryNextId() match
      case Right(value) => assertEquals(value.sequence, 0)
      case Left(err)    => fail("expected Right, got %s".format(err))

    // advance to MaxSequence in the same millisecond
    var i = 1
    while i <= Snowflake.MaxSequence do
      provider.tryNextId() match
        case Right(_)  => ()
        case Left(err) => fail("unexpected error at seq %d: %s".format(i, err))
      i += 1

    provider.tryNextId() match
      case Left(SnowflakeError.SequenceExhausted) => ()
      case other                                  => fail("expected SequenceExhausted, got " + other.toString)
  }

  test("tryNextId reports clock moved backwards beyond tolerance") {
    val t0    = Snowflake.Epoch + 3000L
    val clock = TestClock(Array(t0, t0 - 10L))
    val provider =
      SnowflakeProvider.make(1, clock = () => clock.now(), maxClockBackwardMs = 5L)

    provider.tryNextId() match
      case Left(SnowflakeError.ClockMovedBackwards(delta)) => assertEquals(delta, 10L)
      case other => fail("expected ClockMovedBackwards, got " + other.toString)
  }

  test("tryNextId tolerates small backward skew and stays monotonic") {
    val t0    = Snowflake.Epoch + 4000L
    val t1    = t0 + 1L
    val clock = TestClock(Array(t0, t1, t1 - 1L))
    val provider =
      SnowflakeProvider.make(1, clock = () => clock.now(), maxClockBackwardMs = 5L)

    val first  = provider.tryNextId().toOption.getOrElse(fail("expected Right"))
    val second = provider.tryNextId().toOption.getOrElse(fail("expected Right"))

    assertEquals(first.timestamp, t1)
    assertEquals(second.timestamp, t1)
    assertEquals(second.sequence, first.sequence + 1)
  }
