/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
package com.verschraenkt.ci.engine.api

import java.util.concurrent.atomic.AtomicLong
import scala.annotation.tailrec

sealed trait SnowflakeError extends Exception:
  def msg: String

  override def getMessage: String = msg
object SnowflakeError:
  /** The system clock moved backwards by more than the configured tolerance. */
  final case class ClockMovedBackwards(
      delta: Long
  ) extends SnowflakeError:
    override def msg: String =
      s"System clock moved backwards by $delta ms; refusing to generate Snowflake ID."

  final case class SequenceExhausted(
      msg: String = "Snowflake sequence exhausted within the current millisecond."
  ) extends SnowflakeError

trait SnowflakeProvider:
  /** Blocking variant – spins until an ID is available. May throw on unrecoverable clock drift. */
  def nextId(): Snowflake

  /** Non-blocking variant – returns Left if the sequence is exhausted or the clock went backwards beyond the
    * tolerance window.
    */
  def tryNextId(): Either[SnowflakeError, Snowflake]

object SnowflakeProvider:
  /** @param machineId
    *   Node identifier, 0–1023.
    * @param clock
    *   Millisecond time source (override for testing).
    * @param maxClockBackwardMs
    *   Tolerated backward clock skew before we error. Set to 0 to be strict; 5 is a safe default.
    */
  def make(
      machineId: Int,
      clock: () => Long = () => System.currentTimeMillis(),
      maxClockBackwardMs: Long = 5L
  ): SnowflakeProvider = new Impl(machineId, clock, maxClockBackwardMs)

  private final class Impl(
      machineId: Int,
      clock: () => Long,
      maxClockBackwardMs: Long
  ) extends SnowflakeProvider:

    require(
      machineId >= 0 && machineId <= Snowflake.MaxMachineId,
      s"machineId must be 0–${Snowflake.MaxMachineId}, got $machineId"
    )

    // packed state: (timestampDelta << SequenceBits) | sequence
    // Initialise at MaxSequence so the first call rolls over to seq=0 cleanly.
    private val state = new AtomicLong(
      ((clock() - Snowflake.Epoch) << Snowflake.SequenceBits) | Snowflake.MaxSequence
    )

    // ── Packing helpers ───────────────────────────────────────────────────────

    @inline private def unpackTs(packed: Long): Long =
      (packed >>> Snowflake.SequenceBits) + Snowflake.Epoch

    @inline private def pack(ts: Long, seq: Long): Long =
      ((ts - Snowflake.Epoch) << Snowflake.SequenceBits) | seq

    @inline private def build(ts: Long, seq: Long): Snowflake =
      Snowflake(Snowflake.toLong(machineId, seq.toInt, ts), machineId, seq.toInt, ts)

    // ── Core CAS loop ─────────────────────────────────────────────────────────

    private def generate(blocking: Boolean): Either[SnowflakeError, Snowflake] =
      @tailrec
      def loop(): Either[SnowflakeError, Snowflake] =
        val now    = clock()
        val prev   = state.get()
        val prevTs = unpackTs(prev)

        val drift = prevTs - now // positive = clock went back

        if drift > maxClockBackwardMs then
          // Unacceptable clock regression – fail fast
          Left(SnowflakeError.ClockMovedBackwards(drift))
        else
          // Effective timestamp: never go below prevTs (monotonicity guarantee)
          val effectiveTs = math.max(now, prevTs)
          val prevSeq     = prev & Snowflake.MaxSequence

          val nextSeq =
            if effectiveTs > prevTs then 0
            else prevSeq + 1 // same ms / backward skew → increment

          if nextSeq > Snowflake.MaxSequence then
            // Sequence space exhausted for this millisecond
            if !blocking then Left(SnowflakeError.SequenceExhausted())
            else
              // Yield to give the OS a chance to advance the clock, then retry
              Thread.sleep(1)
              loop()
          else
            val next = pack(effectiveTs, nextSeq)
            if state.compareAndSet(prev, next) then Right(build(effectiveTs, nextSeq))
            else loop() // Lost the CAS race – retry immediately

      loop()

    // ── Public API ────────────────────────────────────────────────────────────

    override def nextId(): Snowflake =
      generate(blocking = true).fold(e => throw e, identity)

    override def tryNextId(): Either[SnowflakeError, Snowflake] =
      generate(blocking = false)
