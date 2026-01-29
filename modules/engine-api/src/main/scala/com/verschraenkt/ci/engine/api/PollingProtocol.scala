package com.verschraenkt.ci.engine.api

import com.verschraenkt.ci.core.model.JobId
import java.time.Duration

// Polling Protocol
case class JobRequest(
  runnerToken: JwtToken,  // Validated JWT token
  currentCapabilities: ExecutorCapabilities,
  concurrentJobs: Int  // how many jobs this executor is currently running
)

case class JobAssignment(
  jobId: JobId,
  jobToken: JwtToken,  // Validated short-lived JWT for this job only
  jobDefinition: JobDefinition,
  artifacts: ArtifactInstructions,
  caches: CacheInstructions,
  secrets: Map[String, SecretReference]  // ref with access control
)

case class NoJobAvailable(retryAfter: Duration)
