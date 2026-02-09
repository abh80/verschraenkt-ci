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

import com.verschraenkt.ci.core.model.{ JobId, StepId }

import java.time.Instant

// Execution Events
sealed trait ExecutionEvent:
  def correlationId: CorrelationId
  def timestamp: Instant

case class JobStarted(
    jobId: JobId,
    executorId: ExecutorId,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent

case class StepStarted(
    jobId: JobId,
    stepId: StepId,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent

case class StepProgress(
    jobId: JobId,
    stepId: StepId,
    progress: Double,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent

case class StepCompleted(
    jobId: JobId,
    stepId: StepId,
    exitCode: Int,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent

case class JobCompleted(
    jobId: JobId,
    status: JobStatus,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent

case class JobFailed(
    jobId: JobId,
    reason: FailureReason,
    correlationId: CorrelationId,
    timestamp: Instant
) extends ExecutionEvent
