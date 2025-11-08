package com.verschraenkt.ci.core.model

import cats.kernel.{ Monoid, Order, Semigroup }

/** Represents computational resources with CPU, memory, GPU and disk requirements
  *
  * @param cpuMilli
  *   CPU millicores (1000 = 1 CPU core)
  * @param memoryMiB
  *   Memory in MiB
  * @param gpu
  *   Number of GPU units
  * @param diskMiB
  *   Disk space in MiB
  */
final case class Resource private (
    cpuMilli: Int,
    memoryMiB: Int,
    gpu: Int,
    diskMiB: Int
):
  /** Adds two resources together, using exact arithmetic that throws on overflow */
  def +(other: Resource): Resource =
    Resource.unsafe(
      Math.addExact(cpuMilli, other.cpuMilli),
      Math.addExact(memoryMiB, other.memoryMiB),
      Math.addExact(gpu, other.gpu),
      Math.addExact(diskMiB, other.diskMiB)
    )

  /** Adds two resources together, saturating at Int.MaxValue instead of overflowing */
  def saturatingAdd(other: Resource): Resource =
    Resource(
      Resource.saturating(cpuMilli, other.cpuMilli),
      Resource.saturating(memoryMiB, other.memoryMiB),
      Resource.saturating(gpu, other.gpu),
      Resource.saturating(diskMiB, other.diskMiB)
    )

  /** Subtracts other resource from this one, flooring at 0 for each component */
  def subtractFloorZero(other: Resource): Resource =
    Resource(
      Math.max(0, cpuMilli - other.cpuMilli),
      Math.max(0, memoryMiB - other.memoryMiB),
      Math.max(0, gpu - other.gpu),
      Math.max(0, diskMiB - other.diskMiB)
    )

  /** Scales this resource by a factor, rounding to nearest non-negative integer */
  def scaleBy(f: Double): Resource =
    Resource(
      Resource.roundNonNeg(cpuMilli * f),
      Resource.roundNonNeg(memoryMiB * f),
      Resource.roundNonNeg(gpu * f),
      Resource.roundNonNeg(diskMiB * f)
    )

  /** Returns true if this resource fits within the given limit */
  def fitsIn(limit: Resource): Boolean =
    cpuMilli <= limit.cpuMilli &&
      memoryMiB <= limit.memoryMiB &&
      gpu <= limit.gpu &&
      diskMiB <= limit.diskMiB

  /** Returns a resource with maximum values from this and other resource */
  def max(other: Resource): Resource =
    Resource(
      Math.max(cpuMilli, other.cpuMilli),
      Math.max(memoryMiB, other.memoryMiB),
      Math.max(gpu, other.gpu),
      Math.max(diskMiB, other.diskMiB)
    )

  /** Returns a resource with minimum values from this and other resource */
  def min(other: Resource): Resource =
    Resource(
      Math.min(cpuMilli, other.cpuMilli),
      Math.min(memoryMiB, other.memoryMiB),
      Math.min(gpu, other.gpu),
      Math.min(diskMiB, other.diskMiB)
    )

/** Companion object for Resource class */
object Resource:
  /** Creates a new Resource instance with validation of non-negative values
    *
    * @param cpuMilli
    *   CPU millicores (must be >= 0)
    * @param memoryMiB
    *   Memory in MiB (must be >= 0)
    * @param gpu
    *   Number of GPUs (defaults to 0)
    * @param diskMiB
    *   Disk space in MiB (defaults to 0)
    * @throws IllegalArgumentException
    *   if any value is negative
    */
  def apply(cpuMilli: Int, memoryMiB: Int, gpu: Int = 0, diskMiB: Int = 0): Resource =
    require(cpuMilli >= 0 && memoryMiB >= 0 && gpu >= 0 && diskMiB >= 0)
    new Resource(cpuMilli, memoryMiB, gpu, diskMiB)

  /** Creates a Resource instance without validation Only for internal use within model package
    */
  private[core] def unsafe(cpuMilli: Int, memoryMiB: Int, gpu: Int, diskMiB: Int): Resource =
    new Resource(cpuMilli, memoryMiB, gpu, diskMiB)

  /** A Resource instance with all zero values */
  val zero: Resource = Resource(0, 0, 0, 0)

  /** Semigroup instance for Resource combining with + */
  given Semigroup[Resource] with
    def combine(x: Resource, y: Resource): Resource = x + y

  /** Monoid instance for Resource with zero as identity */
  given Monoid[Resource] with
    def empty: Resource                             = zero
    def combine(x: Resource, y: Resource): Resource = x + y

  /** Total ordering for Resources */
  given Order[Resource] with
    def compare(x: Resource, y: Resource): Int =
      val c1 = java.lang.Integer.compare(x.cpuMilli, y.cpuMilli)
      if c1 != 0 then c1
      else
        val c2 = java.lang.Integer.compare(x.memoryMiB, y.memoryMiB)
        if c2 != 0 then c2
        else
          val c3 = java.lang.Integer.compare(x.gpu, y.gpu)
          if c3 != 0 then c3
          else java.lang.Integer.compare(x.diskMiB, y.diskMiB)

  /** Helper for saturating arithmetic */
  private def saturating(a: Int, b: Int): Int =
    val r = a.toLong + b.toLong
    if r > Int.MaxValue then Int.MaxValue
    else if r < Int.MinValue then Int.MinValue
    else r.toInt

  /** Helper to round to the nearest non-negative integer */
  private def roundNonNeg(x: Double): Int =
    val v = Math.round(x).toInt
    if v < 0 then 0 else v
