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

import java.time.Instant

/** Snowflake ID layout (64 bits):
  *
  * ┌─────────────────────────────────────────────┬──────────────┬────────────────┐ │ timestamp (41 bits) │
  * machineId │ sequence │ │ ms since custom epoch │ (10 bits) │ (12 bits) │
  * └─────────────────────────────────────────────┴──────────────┴────────────────┘ 63 22 12 0
  *
  *   - Max timestamp : ~69 years from epoch
  *   - Max machines : 1024 (0–1023)
  *   - Max IDs/ms : 4096 per machine
  */
final case class Snowflake(
    value: Long,
    machineId: Int,
    sequence: Int,
    timestamp: Long // wall-clock ms (epoch-absolute)
):
  def toInstant: Instant = Instant.ofEpochMilli(timestamp)
  override def toString: String =
    s"Snowflake(id=$value, machine=$machineId, seq=$sequence, ts=$timestamp)"

object Snowflake:
  val Epoch: Long = 1700000000000L // 2023-11-14 – keep stable forever
  val SequenceBits = 12
  val MachineIdBits = 10
  val TimestampBits = 41
  val TimestampShift: Int = SequenceBits + MachineIdBits // 22
  val MachineShift: Int = SequenceBits // 12
  val MaxMachineId: Int = (1 << MachineIdBits) - 1 // 1023
  val MaxSequence: Int = (1 << SequenceBits) - 1 // 4095
  val MaxTimestampDelta: Long = (1L << TimestampBits) - 1 // ~69 years in ms

  /** Decode a raw Long.  Returns Left(error) for obviously invalid values. */
  def fromLong(value: Long): Either[String, Snowflake] = {
    if (value < 0) return Left(s"Negative snowflake value: $value")
    val delta = value >> TimestampShift
    if (delta > MaxTimestampDelta)
      return Left(s"Timestamp delta $delta exceeds 41-bit range")
    val ts = delta + Epoch
    val mid = ((value >> MachineShift) & MaxMachineId).toInt
    val seq = (value & MaxSequence).toInt
    Right(Snowflake(value, mid, seq, ts))
  }

  /** Unsafe decode – throws on bad input.  Prefer fromLong in production. */
  def unsafeFromLong(value: Long): Snowflake =
    fromLong(value).fold(msg => throw new IllegalArgumentException(msg), identity)

  def toLong(machineId: Int, sequence: Int, timestamp: Long): Long = {
    val delta = timestamp - Epoch
    require(delta >= 0 && delta <= MaxTimestampDelta, s"Timestamp out of range: $timestamp")
    require(machineId >= 0 && machineId <= MaxMachineId, s"machineId out of range: $machineId")
    require(sequence >= 0 && sequence <= MaxSequence, s"sequence out of range: $sequence")
    (delta << TimestampShift) | (machineId.toLong << MachineShift) | sequence.toLong
  }

  /** Monotonic ordering is identical to Long ordering. */
  implicit val ordering: Ordering[Snowflake] = Ordering.by(_.value)