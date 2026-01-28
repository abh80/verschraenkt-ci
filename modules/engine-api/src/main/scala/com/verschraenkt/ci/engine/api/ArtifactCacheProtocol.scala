package com.verschraenkt.ci.engine.api

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

sealed trait CacheScope
object CacheScope {
  case object Job extends CacheScope
  case object Pipeline extends CacheScope
  case object Global extends CacheScope
}