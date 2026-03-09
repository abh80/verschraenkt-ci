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

sealed trait SnowflakeError extends Exception:
  def msg: String

  override def getMessage: String = msg
object SnowflakeError:
  /** The system clock moved backwards by more than the configured tolerance. */
  final case class ClockMovedBackwards(
                                        delta: Long
                                      ) extends SnowflakeError:
    override def msg: String = s"System clock moved backwards by $delta ms; refusing to generate Snowflake ID."

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

object  SnowflakeProvider:
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

    // packed state: (timestampDelta << 12) | sequence
    // Initialise with sequence = MaxSequence so the first call immediately
    // moves to the current millisecond with sequence 0.
    private val state = new AtomicLong(
      ((clock() - Snowflake.Epoch) << Snowflake.SequenceBits) | Snowflake.MaxSequence
    )

    // ── Shared logic ──────────────────────────────────────────────────────────

    private def unpackTimestamp(packed: Long): Long =
      (packed >> Snowflake.SequenceBits) + Snowflake.Epoch

    private def unpackSequence(packed: Long): Int =
      (packed & Snowflake.MaxSequence).toInt

    private def pack(tsDelta: Long, seq: Int): Long =
      (tsDelta << Snowflake.SequenceBits) | seq.toLong

    // Core CAS loop – returns a fully-built Snowflake or a SnowflakeError.
    // `blocking` = true  → spin-wait on sequence exhaustion (nextId path)
    // `blocking` = false → return Left(SequenceExhausted) immediately
    private def generate(blocking: Boolean): Either[SnowflakeError, Snowflake] =
      var result: Either[SnowflakeError, Snowflake] = null
      while result eq null do
        val now     = clock()
        val prev    = state.get()
        val prevTs  = unpackTimestamp(prev)
        val prevSeq = unpackSequence(prev)

        if now < prevTs then
          val drift = prevTs - now
          if drift > maxClockBackwardMs then result = Left(SnowflakeError.ClockMovedBackwards(drift))
          // else: tiny backward skew – reuse prevTs to stay monotonic
          else {
            val newSeq = prevSeq + 1
            if newSeq > Snowflake.MaxSequence then
              if !blocking then result = Left(SnowflakeError.SequenceExhausted())
              // else spin – another CAS iteration will see a fresh ms shortly
            else {
              val next = pack(prevTs - Snowflake.Epoch, newSeq)
              if state.compareAndSet(prev, next) then result = Right(build(prevTs, newSeq))
            }
          }
        else if now == prevTs then
          val newSeq = prevSeq + 1
          if newSeq > Snowflake.MaxSequence then
            if !blocking then result = Left(SnowflakeError.SequenceExhausted())
            // else spin until the clock ticks
          else {
            val next = pack(now - Snowflake.Epoch, newSeq)
            if state.compareAndSet(prev, next) then result = Right(build(now, newSeq))
          }
        else {
          // Clock moved forward – reset sequence to 0
          val next = pack(now - Snowflake.Epoch, 0)
          if state.compareAndSet(prev, next) then result = Right(build(now, 0))
        }
      result

    private def build(ts: Long, seq: Int): Snowflake =
      val raw = Snowflake.toLong(machineId, seq, ts)
      Snowflake(raw, machineId, seq, ts)

    override def nextId(): Snowflake =
      generate(blocking = true).fold(throw _, identity)

    override def tryNextId(): Either[SnowflakeError, Snowflake] =
      generate(blocking = false)
