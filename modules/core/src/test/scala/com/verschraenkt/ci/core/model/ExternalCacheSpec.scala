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

import cats.data.NonEmptyList
import munit.FunSuite

class ExternalCacheSpec extends FunSuite:

  test("S3Cache should implement CacheLike") {
    val key     = CacheKey.literal("s3-cache-key")
    val s3Cache = S3Cache("my-bucket", "us-east-1", key)

    assertEquals(s3Cache.key, key)
    assertEquals(s3Cache.scope, CacheScope.Global)
    assertEquals(s3Cache.bucket, "my-bucket")
    assertEquals(s3Cache.region, "us-east-1")
  }

  test("GCSCache should implement CacheLike") {
    val key      = CacheKey.literal("gcs-cache-key")
    val gcsCache = GCSCache("my-gcs-bucket", key, CacheScope.Branch)

    assertEquals(gcsCache.key, key)
    assertEquals(gcsCache.scope, CacheScope.Branch)
    assertEquals(gcsCache.bucket, "my-gcs-bucket")
  }

  test("External caches can be used in Steps") {
    val key     = CacheKey.literal("libs")
    val s3Cache = S3Cache("deps-bucket", "eu-west-1", key)

    // Create a RestoreCache step using the S3Cache implementation
    // This verifies that Step.RestoreCache accepts our new implementation
    val step: Step.RestoreCache = Step.RestoreCache(s3Cache, NonEmptyList.of("lib/"))(using StepMeta())

    assertEquals(step.cache, s3Cache)
    assert(step.cache.isInstanceOf[S3Cache])
  }
