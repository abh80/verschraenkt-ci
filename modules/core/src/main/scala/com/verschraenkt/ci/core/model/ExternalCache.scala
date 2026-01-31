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

/** Amazon S3 based cache implementation
  *
  * @param bucket
  *   S3 bucket name where cache is stored
  * @param region
  *   AWS region for the S3 bucket
  * @param key
  *   Cache key identifying the cached content
  * @param scope
  *   Cache scope level (defaults to Global)
  */
final case class S3Cache(
    bucket: String,
    region: String,
    key: CacheKey,
    override val scope: CacheScope = CacheScope.Global
) extends CacheLike

/** Google Cloud Storage based cache implementation
  *
  * @param bucket
  *   GCS bucket name where cache is stored
  * @param key
  *   Cache key identifying the cached content
  * @param scope
  *   Cache scope level (defaults to Global)
  */
final case class GCSCache(
    bucket: String,
    key: CacheKey,
    override val scope: CacheScope = CacheScope.Global
) extends CacheLike
