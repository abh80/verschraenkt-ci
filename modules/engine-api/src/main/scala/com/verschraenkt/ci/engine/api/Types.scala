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

import com.verschraenkt.ci.core.model.{ JobId, PipelineId, StepId }
import com.verschraenkt.ci.core.security.SecretScope

sealed trait JobStatus
object JobStatus:
  case object Pending   extends JobStatus
  case object Running   extends JobStatus
  case object Completed extends JobStatus
  case object Failed    extends JobStatus
  case object Cancelled extends JobStatus

sealed trait FailureReason
object FailureReason:
  case class ExecutionError(message: String)       extends FailureReason
  case class Timeout()                             extends FailureReason
  case class ResourceUnavailable(resource: String) extends FailureReason
  case class UnknownError()                        extends FailureReason

case class JobDefinition(
    jobId: JobId,
    steps: List[StepDefinition],
    environment: Map[String, String],
    timeout: Option[Long]
)

case class StepDefinition(
    stepId: StepId,
    name: String,
    command: String,
    args: List[String],
    env: Map[String, String]
)

case class SecretReference(
    secretId: String,
    version: Option[String],
    scope: SecretScope,
    allowedJobs: Set[JobId] = Set.empty,
    allowedWorkflows: Set[String] = Set.empty,
    allowedPipelines: Set[PipelineId] = Set.empty,
    expiresAt: Option[Long] = None
)
