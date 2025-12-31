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
package com.verschraenkt.ci.core.model

import cats.kernel.{ Monoid, Order, Semigroup }
import munit.FunSuite

class ResourceSpec extends FunSuite:

  test("Resource.apply should create valid resource with all parameters") {
    val resource = Resource(1000, 512, 2, 1024)
    assertEquals(resource.cpuMilli, 1000)
    assertEquals(resource.memoryMiB, 512)
    assertEquals(resource.gpu, 2)
    assertEquals(resource.diskMiB, 1024)
  }

  test("Resource.apply should create resource with default gpu and diskMiB") {
    val resource = Resource(2000, 1024)
    assertEquals(resource.cpuMilli, 2000)
    assertEquals(resource.memoryMiB, 1024)
    assertEquals(resource.gpu, 0)
    assertEquals(resource.diskMiB, 0)
  }

  test("Resource.apply should reject negative cpuMilli") {
    intercept[IllegalArgumentException] {
      Resource(-100, 512, 0, 0)
    }
  }

  test("Resource.apply should reject negative memoryMiB") {
    intercept[IllegalArgumentException] {
      Resource(1000, -512, 0, 0)
    }
  }

  test("Resource.apply should reject negative gpu") {
    intercept[IllegalArgumentException] {
      Resource(1000, 512, -1, 0)
    }
  }

  test("Resource.apply should reject negative diskMiB") {
    intercept[IllegalArgumentException] {
      Resource(1000, 512, 0, -1024)
    }
  }

  test("Resource.zero should have all zero values") {
    assertEquals(Resource.zero.cpuMilli, 0)
    assertEquals(Resource.zero.memoryMiB, 0)
    assertEquals(Resource.zero.gpu, 0)
    assertEquals(Resource.zero.diskMiB, 0)
  }

  test("+ should add two resources correctly") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(2000, 256, 2, 512)
    val result = r1 + r2
    assertEquals(result.cpuMilli, 3000)
    assertEquals(result.memoryMiB, 768)
    assertEquals(result.gpu, 3)
    assertEquals(result.diskMiB, 1536)
  }

  test("+ should work with zero resource") {
    val r1     = Resource(1000, 512, 1, 1024)
    val result = r1 + Resource.zero
    assertEquals(result, r1)
  }

  test("+ should throw on overflow for cpuMilli") {
    val r1 = Resource(Int.MaxValue, 100, 0, 0)
    val r2 = Resource(1, 100, 0, 0)
    intercept[ArithmeticException] {
      r1 + r2
    }
  }

  test("+ should throw on overflow for memoryMiB") {
    val r1 = Resource(100, Int.MaxValue, 0, 0)
    val r2 = Resource(100, 1, 0, 0)
    intercept[ArithmeticException] {
      r1 + r2
    }
  }

  test("saturatingAdd should add two resources correctly") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(2000, 256, 2, 512)
    val result = r1.saturatingAdd(r2)
    assertEquals(result.cpuMilli, 3000)
    assertEquals(result.memoryMiB, 768)
    assertEquals(result.gpu, 3)
    assertEquals(result.diskMiB, 1536)
  }

  test("saturatingAdd should saturate at Int.MaxValue for cpuMilli") {
    val r1     = Resource(Int.MaxValue, 100, 0, 0)
    val r2     = Resource(1000, 100, 0, 0)
    val result = r1.saturatingAdd(r2)
    assertEquals(result.cpuMilli, Int.MaxValue)
  }

  test("saturatingAdd should saturate at Int.MaxValue for all fields") {
    val r1     = Resource(Int.MaxValue, Int.MaxValue, Int.MaxValue, Int.MaxValue)
    val r2     = Resource(1, 1, 1, 1)
    val result = r1.saturatingAdd(r2)
    assertEquals(result.cpuMilli, Int.MaxValue)
    assertEquals(result.memoryMiB, Int.MaxValue)
    assertEquals(result.gpu, Int.MaxValue)
    assertEquals(result.diskMiB, Int.MaxValue)
  }

  test("subtractFloorZero should subtract resources correctly") {
    val r1     = Resource(3000, 768, 3, 1536)
    val r2     = Resource(1000, 256, 1, 512)
    val result = r1.subtractFloorZero(r2)
    assertEquals(result.cpuMilli, 2000)
    assertEquals(result.memoryMiB, 512)
    assertEquals(result.gpu, 2)
    assertEquals(result.diskMiB, 1024)
  }

  test("subtractFloorZero should floor at zero when subtracting larger value") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(2000, 1024, 2, 2048)
    val result = r1.subtractFloorZero(r2)
    assertEquals(result.cpuMilli, 0)
    assertEquals(result.memoryMiB, 0)
    assertEquals(result.gpu, 0)
    assertEquals(result.diskMiB, 0)
  }

  test("subtractFloorZero with zero should return same resource") {
    val r1     = Resource(1000, 512, 1, 1024)
    val result = r1.subtractFloorZero(Resource.zero)
    assertEquals(result, r1)
  }

  test("scaleBy should scale resource by positive factor") {
    val r      = Resource(1000, 500, 2, 1000)
    val result = r.scaleBy(2.0)
    assertEquals(result.cpuMilli, 2000)
    assertEquals(result.memoryMiB, 1000)
    assertEquals(result.gpu, 4)
    assertEquals(result.diskMiB, 2000)
  }

  test("scaleBy should scale resource by fractional factor") {
    val r      = Resource(1000, 500, 4, 1000)
    val result = r.scaleBy(0.5)
    assertEquals(result.cpuMilli, 500)
    assertEquals(result.memoryMiB, 250)
    assertEquals(result.gpu, 2)
    assertEquals(result.diskMiB, 500)
  }

  test("scaleBy should floor negative results at zero") {
    val r      = Resource(1000, 500, 2, 1000)
    val result = r.scaleBy(-1.0)
    assertEquals(result.cpuMilli, 0)
    assertEquals(result.memoryMiB, 0)
    assertEquals(result.gpu, 0)
    assertEquals(result.diskMiB, 0)
  }

  test("scaleBy should round to nearest integer") {
    val r      = Resource(100, 100, 100, 100)
    val result = r.scaleBy(1.55)
    assertEquals(result.cpuMilli, 155)
    assertEquals(result.memoryMiB, 155)
    assertEquals(result.gpu, 155)
    assertEquals(result.diskMiB, 155)
  }

  test("scaleBy with zero should return zero resource") {
    val r      = Resource(1000, 500, 2, 1000)
    val result = r.scaleBy(0.0)
    assertEquals(result, Resource.zero)
  }

  test("fitsIn should return true when all components fit") {
    val r     = Resource(1000, 512, 1, 1024)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(r.fitsIn(limit))
  }

  test("fitsIn should return true when components are equal") {
    val r = Resource(1000, 512, 1, 1024)
    assert(r.fitsIn(r))
  }

  test("fitsIn should return false when cpuMilli exceeds limit") {
    val r     = Resource(3000, 512, 1, 1024)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(!r.fitsIn(limit))
  }

  test("fitsIn should return false when memoryMiB exceeds limit") {
    val r     = Resource(1000, 2048, 1, 1024)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(!r.fitsIn(limit))
  }

  test("fitsIn should return false when gpu exceeds limit") {
    val r     = Resource(1000, 512, 3, 1024)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(!r.fitsIn(limit))
  }

  test("fitsIn should return false when diskMiB exceeds limit") {
    val r     = Resource(1000, 512, 1, 3072)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(!r.fitsIn(limit))
  }

  test("fitsIn should return false when any component exceeds limit") {
    val r     = Resource(1000, 512, 1, 2049)
    val limit = Resource(2000, 1024, 2, 2048)
    assert(!r.fitsIn(limit))
  }

  test("max should return maximum values from two resources") {
    val r1     = Resource(1000, 1024, 1, 512)
    val r2     = Resource(500, 2048, 2, 256)
    val result = r1.max(r2)
    assertEquals(result.cpuMilli, 1000)
    assertEquals(result.memoryMiB, 2048)
    assertEquals(result.gpu, 2)
    assertEquals(result.diskMiB, 512)
  }

  test("max with same resource should return same resource") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = r.max(r)
    assertEquals(result, r)
  }

  test("max with zero should return original resource") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = r.max(Resource.zero)
    assertEquals(result, r)
  }

  test("min should return minimum values from two resources") {
    val r1     = Resource(1000, 1024, 1, 512)
    val r2     = Resource(500, 2048, 2, 256)
    val result = r1.min(r2)
    assertEquals(result.cpuMilli, 500)
    assertEquals(result.memoryMiB, 1024)
    assertEquals(result.gpu, 1)
    assertEquals(result.diskMiB, 256)
  }

  test("min with same resource should return same resource") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = r.min(r)
    assertEquals(result, r)
  }

  test("min with zero should return zero resource") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = r.min(Resource.zero)
    assertEquals(result, Resource.zero)
  }

  test("Semigroup should combine resources") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(500, 256, 0, 512)
    val result = Semigroup[Resource].combine(r1, r2)
    assertEquals(result.cpuMilli, 1500)
    assertEquals(result.memoryMiB, 768)
    assertEquals(result.gpu, 1)
    assertEquals(result.diskMiB, 1536)
  }

  test("Monoid should have zero as empty") {
    val empty = Monoid[Resource].empty
    assertEquals(empty, Resource.zero)
  }

  test("Monoid should combine resources") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(500, 256, 0, 512)
    val result = Monoid[Resource].combine(r1, r2)
    assertEquals(result.cpuMilli, 1500)
    assertEquals(result.memoryMiB, 768)
  }

  test("Monoid should satisfy left identity law") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = Monoid[Resource].combine(Monoid[Resource].empty, r)
    assertEquals(result, r)
  }

  test("Monoid should satisfy right identity law") {
    val r      = Resource(1000, 512, 1, 1024)
    val result = Monoid[Resource].combine(r, Monoid[Resource].empty)
    assertEquals(result, r)
  }

  test("Order should compare equal resources as equal") {
    val r1 = Resource(1000, 512, 1, 1024)
    val r2 = Resource(1000, 512, 1, 1024)
    assertEquals(Order[Resource].compare(r1, r2), 0)
  }

  test("Order should compare by cpuMilli first") {
    val r1 = Resource(1000, 512, 1, 1024)
    val r2 = Resource(2000, 512, 1, 1024)
    assert(Order[Resource].compare(r1, r2) < 0)
    assert(Order[Resource].compare(r2, r1) > 0)
  }

  test("Order should compare by memoryMiB when cpuMilli is equal") {
    val r1 = Resource(1000, 512, 1, 1024)
    val r2 = Resource(1000, 1024, 1, 1024)
    assert(Order[Resource].compare(r1, r2) < 0)
    assert(Order[Resource].compare(r2, r1) > 0)
  }

  test("Order should compare by gpu when cpuMilli and memoryMiB are equal") {
    val r1 = Resource(1000, 512, 1, 1024)
    val r2 = Resource(1000, 512, 2, 1024)
    assert(Order[Resource].compare(r1, r2) < 0)
    assert(Order[Resource].compare(r2, r1) > 0)
  }

  test("Order should compare by diskMiB when other fields are equal") {
    val r1 = Resource(1000, 512, 1, 1024)
    val r2 = Resource(1000, 512, 1, 2048)
    assert(Order[Resource].compare(r1, r2) < 0)
    assert(Order[Resource].compare(r2, r1) > 0)
  }

  test("Order should handle comparison with zero resource") {
    val r = Resource(1000, 512, 1, 1024)
    assert(Order[Resource].compare(r, Resource.zero) > 0)
    assert(Order[Resource].compare(Resource.zero, r) < 0)
  }

  test("Multiple operations should compose correctly") {
    val r1     = Resource(1000, 512, 1, 1024)
    val r2     = Resource(500, 256, 1, 512)
    val sum    = r1 + r2
    val scaled = sum.scaleBy(0.5)
    val maxed  = scaled.max(r1)

    assertEquals(maxed.cpuMilli, 1000)
    assertEquals(maxed.memoryMiB, 512)
    assertEquals(maxed.gpu, 1)
    assertEquals(maxed.diskMiB, 1024)
  }

  test("Resource operations should be chainable") {
    val result = Resource(2000, 1024, 2, 2048)
      .subtractFloorZero(Resource(500, 256, 1, 512))
      .scaleBy(0.5)
      .max(Resource(500, 400, 1, 500))

    assertEquals(result.cpuMilli, 750)
    assertEquals(result.memoryMiB, 400)
    assertEquals(result.gpu, 1)
    assertEquals(result.diskMiB, 768)
  }

  test("fitsIn should work with complex resource hierarchies") {
    val small  = Resource(500, 256, 0, 512)
    val medium = Resource(1000, 512, 1, 1024)
    val large  = Resource(2000, 1024, 2, 2048)

    assert(small.fitsIn(medium))
    assert(medium.fitsIn(large))
    assert(small.fitsIn(large))
    assert(!large.fitsIn(medium))
    assert(!medium.fitsIn(small))
  }
