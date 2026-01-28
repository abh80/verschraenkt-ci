package com.verschraenkt.ci.engine.api

import com.verschraenkt.ci.core.model.JobId
import java.time.Duration

// Polling Protocol
case class JobRequest(
  runnerToken: String,
  currentCapabilities: ExecutorCapabilities,
  concurrentJobs: Int  // how many jobs this executor is currently running
)

case class JobAssignment(
  jobId: JobId,
  jobToken: String,  // short-lived JWT for this job only
  jobDefinition: JobDefinition,
  artifacts: ArtifactInstructions,
  caches: CacheInstructions,
  secrets: Map[String, SecretReference]  // ref only, not values
)

case class NoJobAvailable(retryAfter: Duration)
