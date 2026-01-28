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