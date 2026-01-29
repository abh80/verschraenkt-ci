package com.verschraenkt.ci.engine.api

// Registration Protocol
case class RegisterExecutorRequest(
  registrationToken: String,
  capabilities: ExecutorCapabilities,
  labels: Map[String, String]
)

case class RegisterExecutorResponse(
  executorId: ExecutorId,
  runnerToken: JwtToken  // Validated long-lived JWT
)

case class ExecutorCapabilities(
  platform: String,  // "linux", "windows", "macos"
  architectures: Set[String],  // "amd64", "arm64"
  resources: AvailableResources,
  runtimes: Set[String],  // "docker", "kubernetes", "shell"
  featureFlags: Set[String]  // "gpu", "privileged", etc.
)

case class AvailableResources(
  cpu: Int,
  memory: Long,  // in bytes
  disk: Long,    // in bytes
  gpu: Option[Int]
)