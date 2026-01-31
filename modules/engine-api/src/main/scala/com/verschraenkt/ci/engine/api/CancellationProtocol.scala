package com.verschraenkt.ci.engine.api

import com.verschraenkt.ci.core.model.JobId

import java.time.Duration

// Cancellation Protocol
case class CancelJobRequest(
    jobId: JobId,
    reason: String
)

case class CancelJobAcknowledged(
    jobId: JobId,
    gracePeriod: Duration
)
