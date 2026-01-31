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
package com.verschraenkt.ci.core.security

import com.verschraenkt.ci.core.errors.ValidationError
import com.verschraenkt.ci.core.model.{ JobId, PipelineId }

/** Secret scope levels */
enum SecretScope:
  case Job      // Secret is scoped to a specific job
  case Workflow // Secret is scoped to a workflow (all jobs in workflow)
  case Pipeline // Secret is scoped to a pipeline (all workflows/jobs)
  case Global   // Secret is globally accessible

/** Secret access control information */
case class SecretAccessControl(
    scope: SecretScope,
    allowedJobs: Set[JobId] = Set.empty,
    allowedWorkflows: Set[String] = Set.empty,
    allowedPipelines: Set[PipelineId] = Set.empty,
    expiresAt: Option[Long] = None
)

/** Context for secret access validation */
case class SecretAccessContext(
    requestingJobId: JobId,
    workflowName: String,
    pipelineId: PipelineId,
    timestamp: Long = System.currentTimeMillis() / 1000
)

/** Utility for validating secret access permissions */
object SecretAccessControl:

  /** Check if a job has permission to access a secret
    * @param secretId
    *   The secret identifier
    * @param accessControl
    *   The access control rules for the secret
    * @param context
    *   The context of the access request
    * @return
    *   Either an error or unit on success
    */
  def validateAccess(
      secretId: String,
      accessControl: SecretAccessControl,
      context: SecretAccessContext
  ): Either[ValidationError, Unit] =
    // Check expiration first
    accessControl.expiresAt match
      case Some(exp) if exp < context.timestamp =>
        return Left(
          ValidationError(
            s"Secret '$secretId' has expired (exp=$exp, now=${context.timestamp})",
            Some("secret.expired")
          )
        )
      case _ => ()

    // Check scope-based access
    accessControl.scope match
      case SecretScope.Global =>
        // Global secrets are accessible by all
        Right(())

      case SecretScope.Pipeline =>
        // Check if requesting from allowed pipeline
        if accessControl.allowedPipelines.isEmpty ||
          accessControl.allowedPipelines.contains(context.pipelineId)
        then Right(())
        else
          Left(
            ValidationError(
              s"Job '${context.requestingJobId.value}' does not have access to pipeline-scoped secret '$secretId'",
              Some("secret.access.denied")
            )
          )

      case SecretScope.Workflow =>
        // Check if requesting from allowed workflow
        if accessControl.allowedWorkflows.isEmpty ||
          accessControl.allowedWorkflows.contains(context.workflowName)
        then Right(())
        else
          Left(
            ValidationError(
              s"Job '${context.requestingJobId.value}' in workflow '${context.workflowName}' does not have access to workflow-scoped secret '$secretId'",
              Some("secret.access.denied")
            )
          )

      case SecretScope.Job =>
        // Check if requesting job is explicitly allowed
        if accessControl.allowedJobs.isEmpty ||
          accessControl.allowedJobs.contains(context.requestingJobId)
        then Right(())
        else
          Left(
            ValidationError(
              s"Job '${context.requestingJobId.value}' does not have access to job-scoped secret '$secretId'",
              Some("secret.access.denied")
            )
          )

  /** Check access with hierarchical scope inheritance In hierarchical mode:
    *   - Job scope: only specified jobs
    *   - Workflow scope: all jobs in specified workflows
    *   - Pipeline scope: all jobs in specified pipelines
    *   - Global scope: all jobs everywhere
    */
  def validateAccessHierarchical(
      secretId: String,
      accessControl: SecretAccessControl,
      context: SecretAccessContext
  ): Either[ValidationError, Unit] =
    // First check expiration
    accessControl.expiresAt match
      case Some(exp) if exp < context.timestamp =>
        return Left(
          ValidationError(
            s"Secret '$secretId' has expired",
            Some("secret.expired")
          )
        )
      case _ => ()

    // Hierarchical check: Global > Pipeline > Workflow > Job
    accessControl.scope match
      case SecretScope.Global =>
        Right(())

      case SecretScope.Pipeline =>
        if accessControl.allowedPipelines.isEmpty ||
          accessControl.allowedPipelines.contains(context.pipelineId)
        then Right(())
        else
          Left(
            ValidationError(
              s"Pipeline '${context.pipelineId.value}' does not have access to secret '$secretId'",
              Some("secret.access.denied")
            )
          )

      case SecretScope.Workflow =>
        // Allow if either pipeline or workflow matches
        val pipelineMatch = accessControl.allowedPipelines.isEmpty ||
          accessControl.allowedPipelines.contains(context.pipelineId)
        val workflowMatch = accessControl.allowedWorkflows.isEmpty ||
          accessControl.allowedWorkflows.contains(context.workflowName)

        if pipelineMatch && workflowMatch then Right(())
        else
          Left(
            ValidationError(
              s"Workflow '${context.workflowName}' does not have access to secret '$secretId'",
              Some("secret.access.denied")
            )
          )

      case SecretScope.Job =>
        // Most restrictive: must match job explicitly
        if accessControl.allowedJobs.isEmpty ||
          accessControl.allowedJobs.contains(context.requestingJobId)
        then Right(())
        else
          Left(
            ValidationError(
              s"Job '${context.requestingJobId.value}' does not have access to secret '$secretId'",
              Some("secret.access.denied")
            )
          )

  /** Create a default global secret access control */
  def global: SecretAccessControl =
    SecretAccessControl(scope = SecretScope.Global)

  /** Create a pipeline-scoped secret access control */
  def forPipeline(pipelineIds: Set[PipelineId]): SecretAccessControl =
    SecretAccessControl(
      scope = SecretScope.Pipeline,
      allowedPipelines = pipelineIds
    )

  /** Create a workflow-scoped secret access control */
  def forWorkflow(workflowNames: Set[String]): SecretAccessControl =
    SecretAccessControl(
      scope = SecretScope.Workflow,
      allowedWorkflows = workflowNames
    )

  /** Create a job-scoped secret access control */
  def forJob(jobIds: Set[JobId]): SecretAccessControl =
    SecretAccessControl(
      scope = SecretScope.Job,
      allowedJobs = jobIds
    )
