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

import com.verschraenkt.ci.core.model.CacheScope

// Artifact & Cache Protocol
case class ArtifactInstructions(
    downloads: List[ArtifactDownload],
    uploads: List[ArtifactUpload]
)

case class ArtifactDownload(
    name: String,
    url: String,
    path: String
)

case class ArtifactUpload(
    name: String,
    path: String
)

case class CacheInstructions(
    scope: CacheScope,
    key: String,
    paths: List[String],
    restoreKeys: List[String]
)
