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
package com.verschraenkt.ci.core.validation

import cats.data.{ NonEmptyList, NonEmptyVector, Validated, ValidatedNel }
import cats.implicits.*
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.errors.{ CompositeError, ValidationError }
import com.verschraenkt.ci.core.model.*
import com.verschraenkt.ci.core.security.{ CommandSanitizer, ContainerImageConfig, ContainerImageValidator }
import com.verschraenkt.ci.core.utils.DAG

import scala.concurrent.duration.*

object ValidationService:
  type ValidationResult[A] = ValidatedNel[ValidationError, A]

  def validatePipeline(pipeline: Pipeline)(using ctx: ApplicationContext): Either[CompositeError, Pipeline] =
    val result = (
      validatePipelineId(pipeline.id),
      validateWorkflows(pipeline.workflows),
      validateUniqueWorkflowNames(pipeline.workflows),
      validateLabels(pipeline.labels, "Pipeline label"),
      validateTimeout(pipeline.timeout, "pipeline")
    ).mapN { (id, workflows, _, _, _) =>
      pipeline.copy(id = id, workflows = workflows)
    }

    result match
      case Validated.Valid(p)      => Right(p)
      case Validated.Invalid(errs) => Left(CompositeError(errs.toList.toVector))

  def validateWorkflow(workflow: Workflow)(using ctx: ApplicationContext): ValidationResult[Workflow] =
    (
      validateWorkflowName(workflow.name),
      validateJobs(workflow.jobs),
      validateLabels(workflow.labels, "Workflow label"),
      validateContainer(workflow.defaultContainer),
      validateCondition(workflow.condition)
    ).mapN { (name, jobs, _, _, _) =>
      workflow.copy(name = name, jobs = jobs)
    }

  def validateJob(job: Job)(using ctx: ApplicationContext): ValidationResult[Job] =
    (
      validateJobId(job.id),
      validateSteps(job.steps),
      validateStepIds(job.steps),
      validateDependencies(job.dependencies, job.id),
      validateResource(job.resources),
      validateTimeout(Some(job.timeout), "job"),
      validateMatrix(job.matrix),
      validateContainer(job.container),
      validateLabels(job.labels, "Job label"),
      validateCondition(job.condition)
    ).mapN { (id, steps, _, _, resource, _, _, _, _, _) =>
      job.copy(id = id, steps = steps, resources = resource)
    }

  def validateStep(step: Step)(using ctx: ApplicationContext): ValidationResult[Step] =
    step match
      case s: Step.Checkout     => validateCheckout(s)
      case s: Step.Run          => validateRun(s)
      case s: Step.RestoreCache => validateRestoreCache(s)
      case s: Step.SaveCache    => validateSaveCache(s)
      case s: Step.Composite    => validateCompositeStep(s)

  private def validatePipelineId(
      id: PipelineId
  )(using ctx: ApplicationContext): ValidationResult[PipelineId] =
    if id.value.trim.isEmpty then ctx.validation("Pipeline ID cannot be empty").invalidNel
    else if id.value.length > 255 then ctx.validation("Pipeline ID cannot exceed 255 characters").invalidNel
    else if !id.value.matches("[a-zA-Z0-9_-]+") then
      ctx
        .validation("Pipeline ID must contain only alphanumeric characters, underscores, and hyphens")
        .invalidNel
    else id.validNel

  private def validateWorkflows(workflows: NonEmptyVector[Workflow])(using
      ctx: ApplicationContext
  ): ValidationResult[NonEmptyVector[Workflow]] =
    val validated = workflows.toVector.zipWithIndex.map { case (w, idx) =>
      validateWorkflow(w)(using ctx.child(s"workflow[$idx]"))
    }
    validated.sequence.map(v => NonEmptyVector.fromVectorUnsafe(v))

  private def validateWorkflowName(name: String)(using ctx: ApplicationContext): ValidationResult[String] =
    if name.trim.isEmpty then ctx.validation("Workflow name cannot be empty").invalidNel
    else if name.length > 255 then ctx.validation("Workflow name cannot exceed 255 characters").invalidNel
    else name.validNel

  private def validateJobs(jobs: NonEmptyVector[Job])(using
      ctx: ApplicationContext
  ): ValidationResult[NonEmptyVector[Job]] =
    val validated = jobs.toVector.zipWithIndex.map { case (j, idx) =>
      validateJob(j)(using ctx.child(s"job[${j.id.value}]"))
    }

    val validationResult = validated.sequence.map(v => NonEmptyVector.fromVectorUnsafe(v))

    validationResult.andThen { validJobs =>
      validateJobDependenciesWithDAG(validJobs)
    }

  private def validateJobId(id: JobId)(using ctx: ApplicationContext): ValidationResult[JobId] =
    if id.value.trim.isEmpty then ctx.validation("Job ID cannot be empty").invalidNel
    else if id.value.length > 255 then ctx.validation("Job ID cannot exceed 255 characters").invalidNel
    else if !id.value.matches("[a-zA-Z0-9_-]+") then
      ctx.validation("Job ID must contain only alphanumeric characters, underscores, and hyphens").invalidNel
    else id.validNel

  private def validateSteps(steps: NonEmptyVector[Step])(using
      ctx: ApplicationContext
  ): ValidationResult[NonEmptyVector[Step]] =
    val validated = steps.toVector.zipWithIndex.map { case (s, idx) =>
      validateStep(s)(using ctx.child(s"step[$idx]"))
    }
    validated.sequence.map(v => NonEmptyVector.fromVectorUnsafe(v))

  private def validateDependencies(deps: Set[JobId], currentJobId: JobId)(using
      ctx: ApplicationContext
  ): ValidationResult[Set[JobId]] =
    if deps.contains(currentJobId) then
      ctx.validation(s"Job ${currentJobId.value} cannot depend on itself").invalidNel
    else deps.validNel

  private def validateJobDependenciesWithDAG(jobs: NonEmptyVector[Job])(using
      ctx: ApplicationContext
  ): ValidationResult[NonEmptyVector[Job]] =
    DAG.order(jobs.toVector.toList) match
      case Right(_)  => jobs.validNel
      case Left(err) => err.asInstanceOf[ValidationError].invalidNel

  private def validateResource(resource: Resource)(using
      ctx: ApplicationContext
  ): ValidationResult[Resource] =
    val errors = Vector.newBuilder[ValidationError]

    if resource.cpuMilli <= 0 then errors += ctx.validation("CPU must be positive")

    if resource.memoryMiB <= 0 then errors += ctx.validation("Memory must be positive")

    if resource.gpu < 0 then errors += ctx.validation("GPU count cannot be negative")

    if resource.diskMiB < 0 then errors += ctx.validation("Disk space cannot be negative")

    val errs = errors.result()
    if errs.isEmpty then resource.validNel
    else Validated.invalid(NonEmptyList.fromListUnsafe(errs.toList))

  private def validateTimeout(timeout: Option[FiniteDuration], context: String)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    timeout match
      case None => ().validNel
      case Some(t) if t <= 0.seconds =>
        ctx.validation(s"Timeout for $context must be positive").invalidNel
      case Some(t) if t > 24.hours =>
        ctx.validation(s"Timeout for $context cannot exceed 24 hours").invalidNel
      case _ => ().validNel

  private def validateMatrix(matrix: Map[String, NonEmptyVector[String]])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    val errors = Vector.newBuilder[ValidationError]

    matrix.foreach { case (key, values) =>
      if key.trim.isEmpty then errors += ctx.validation("Matrix key cannot be empty")

      if values.length > 256 then errors += ctx.validation(s"Matrix key '$key' has too many values (max 256)")

      if values.toVector.exists(_.trim.isEmpty) then
        errors += ctx.validation(s"Matrix key '$key' contains empty values")
    }

    val totalCombinations = matrix.values.map(_.length.toLong).product
    if totalCombinations > 256 then
      errors += ctx.validation(s"Matrix generates too many combinations: $totalCombinations (max 256)")

    val errs = errors.result()
    if errs.isEmpty then ().validNel
    else Validated.invalid(NonEmptyList.fromListUnsafe(errs.toList))

  private def validateContainer(container: Option[Container])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    container match
      case None    => ().validNel
      case Some(c) =>
        // Use ContainerImageValidator with a lenient config for now
        // TODO: Make this configurable via system settings
        val config = ContainerImageConfig(
          approvedRegistries = Set("docker.io", "ghcr.io", "gcr.io", "quay.io"),
          requireDigest = false, // TODO: Enable in production
          allowLatestTag = true, // TODO: Disable in production
          allowUnspecifiedRegistry = false
        )

        if c.image.trim.isEmpty then ctx.validation("Container image cannot be empty").invalidNel
        else if c.image.length > 512 then
          ctx.validation("Container image name cannot exceed 512 characters").invalidNel
        else
          ContainerImageValidator.validate(c.image, config) match
            case Right(_)  => ().validNel
            case Left(err) => err.invalidNel

  private def validateCheckout(checkout: Step.Checkout)(using
      ctx: ApplicationContext
  ): ValidationResult[Step] =
    validateStepMeta(checkout.meta).map(_ => checkout)

  private def validateRun(run: Step.Run)(using ctx: ApplicationContext): ValidationResult[Step] =
    (
      validateCommand(run.command),
      validateStepMeta(run.meta)
    ).mapN((_, _) => run)

  private def validateRestoreCache(
      restore: Step.RestoreCache
  )(using ctx: ApplicationContext): ValidationResult[Step] =
    (
      validateCacheLike(restore.cache),
      validatePaths(restore.paths),
      validateStepMeta(restore.meta)
    ).mapN((_, _, _) => restore)

  private def validateSaveCache(save: Step.SaveCache)(using ctx: ApplicationContext): ValidationResult[Step] =
    (
      validateCacheLike(save.cache),
      validatePaths(save.paths),
      validateStepMeta(save.meta)
    ).mapN((_, _, _) => save)

  private def validateCompositeStep(composite: Step.Composite)(using
      ctx: ApplicationContext
  ): ValidationResult[Step] =
    val validated = composite.steps.toVector.zipWithIndex.map { case (s, idx) =>
      validateStep(s)(using ctx.child(s"composite[$idx]"))
    }
    validated.sequence.map(_ => composite)

  private def validateStepMeta(meta: StepMeta)(using ctx: ApplicationContext): ValidationResult[Unit] =
    validateTimeout(meta.timeout, "step").andThen { _ =>
      if meta.retry.exists(_.maxAttempts < 1) then
        ctx.validation("Retry max attempts must be at least 1").invalidNel
      else if meta.retry.exists(_.maxAttempts > 10) then
        ctx.validation("Retry max attempts cannot exceed 10").invalidNel
      else if meta.retry.exists(_.delay <= 0.seconds) then
        ctx.validation("Retry delay must be positive").invalidNel
      else ().validNel
    }

  private def validateCommand(command: CommandLike)(using ctx: ApplicationContext): ValidationResult[Unit] =
    // Create a default policy for validation
    // TODO: Make this configurable via system settings or per-job
    val defaultPolicy = Policy(
      allowShell = true,
      maxTimeoutSec = 86400, // 24 hours
      denyPatterns = List(
        "rm -rf /",
        // Fork bomb pattern removed due to escaping issues
        "dd if=/dev/zero",
        "mkfs"
      ),
      allowedExecutables = None, // No allowlist by default
      blockEnvironmentVariables = Set("AWS_SECRET_ACCESS_KEY", "GITHUB_TOKEN")
    )

    command.asCommand match
      case Command.Exec(program, _, _, _, timeout) =>
        if program.trim.isEmpty then ctx.validation("Exec program cannot be empty").invalidNel
        else
          (
            validateCommandTimeout(timeout),
            CommandSanitizer.validate(command.asCommand, defaultPolicy)
          ).mapN((_, _) => ())

      case Command.Shell(script, _, _, _, timeout) =>
        if script.trim.isEmpty then ctx.validation("Shell script cannot be empty").invalidNel
        else
          (
            validateCommandTimeout(timeout),
            CommandSanitizer.validate(command.asCommand, defaultPolicy)
          ).mapN((_, _) => ())

      case Command.Composite(steps) =>
        val validated = steps.toVector.map(validateCommand)
        validated.sequence.map(_ => ())

  private def validateCommandTimeout(timeout: Option[Int])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    timeout match
      case None => ().validNel
      case Some(t) if t <= 0 =>
        ctx.validation("Command timeout must be positive").invalidNel
      case Some(t) if t > 86400 =>
        ctx.validation("Command timeout cannot exceed 24 hours (86400 seconds)").invalidNel
      case _ => ().validNel

  private def validateCacheLike(cache: CacheLike)(using ctx: ApplicationContext): ValidationResult[Unit] =
    if cache.key.value.trim.isEmpty then ctx.validation("Cache key cannot be empty").invalidNel
    else if cache.key.value.length > 128 then
      ctx.validation("Cache key cannot exceed 128 characters").invalidNel
    else ().validNel

  private def validatePaths(paths: NonEmptyList[String])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    val errors = Vector.newBuilder[ValidationError]

    paths.toList.zipWithIndex.foreach { case (path, idx) =>
      if path.trim.isEmpty then errors += ctx.validation(s"Path at index $idx cannot be empty")

      if path.length > 1024 then errors += ctx.validation(s"Path at index $idx cannot exceed 1024 characters")
    }

    val errs = errors.result()
    if errs.isEmpty then ().validNel
    else Validated.invalid(NonEmptyList.fromListUnsafe(errs.toList))

  private def validateCondition(condition: Condition, depth: Int = 0)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    if depth > 50 then return ctx.validation("Condition nesting depth exceeds maximum of 50").invalidNel

    condition match
      case Condition.Always | Condition.Never | Condition.OnSuccess | Condition.OnFailure |
          Condition.OnManualTrigger | Condition.OnDraft | Condition.NotOnDraft | Condition.OnPullRequest |
          Condition.OnWorkflowDispatch | Condition.OnWorkflowCall | Condition.IsFork | Condition.IsNotFork |
          Condition.OnCancelled | Condition.IsPrivate | Condition.IsPublic =>
        ().validNel

      case Condition.OnBranch(pattern, _) =>
        validatePattern(pattern, "branch")

      case Condition.NotOnBranch(pattern, _) =>
        validatePattern(pattern, "branch")

      case Condition.OnTag(pattern, _) =>
        validatePattern(pattern, "tag")

      case Condition.NotOnTag(pattern, _) =>
        validatePattern(pattern, "tag")

      case Condition.OnCommitMessage(pattern, _) =>
        validatePattern(pattern, "commit message")

      case Condition.OnEvent(event) =>
        validateString(event, "Event name", 128)

      case Condition.NotOnEvent(event) =>
        validateString(event, "Event name", 128)

      case Condition.OnPathsChanged(paths) =>
        paths.toVector.traverse_(path => validatePathPattern(path))

      case Condition.OnPathsNotChanged(paths) =>
        paths.toVector.traverse_(path => validatePathPattern(path))

      case Condition.EnvEquals(key, value) =>
        validateEnvKey(key).productR(validateEnvValue(value))

      case Condition.EnvNotEquals(key, value) =>
        validateEnvKey(key).productR(validateEnvValue(value))

      case Condition.EnvExists(key) =>
        validateEnvKey(key)

      case Condition.EnvNotExists(key) =>
        validateEnvKey(key)

      case Condition.OnSchedule(cron) =>
        validateString(cron, "Cron expression", 256)

      case Condition.OnAuthor(author) =>
        validateString(author, "Author name", 256)

      case Condition.NotOnAuthor(author) =>
        validateString(author, "Author name", 256)

      case Condition.OnRepository(repo) =>
        validateString(repo, "Repository name", 256)

      case Condition.Expression(expr) =>
        if expr.trim.isEmpty then ctx.validation("Expression cannot be empty").invalidNel
        else if expr.length > 4096 then ctx.validation("Expression cannot exceed 4096 characters").invalidNel
        else ().validNel

      case Condition.And(conditions) =>
        conditions.toVector.traverse_(cond => validateCondition(cond, depth + 1))

      case Condition.Or(conditions) =>
        conditions.toVector.traverse_(cond => validateCondition(cond, depth + 1))

      case Condition.Not(cond) =>
        validateCondition(cond, depth + 1)

      case Condition.HasLabel(label) =>
        validateLabel(label, "Label name")

      case Condition.HasAllLabels(labels) =>
        labels.toVector.traverse_(label => validateLabel(label, "Label name"))

      case Condition.HasAnyLabel(labels) =>
        labels.toVector.traverse_(label => validateLabel(label, "Label name"))

      case Condition.NotHasLabel(label) =>
        validateLabel(label, "Label name")

      case Condition.ActorIs(username) =>
        validateString(username, "Username", 256)

      case Condition.ActorIsNot(username) =>
        validateString(username, "Username", 256)

      case Condition.ActorInTeam(team) =>
        validateString(team, "Team name", 256)

      case Condition.HasPermission(permission) =>
        validateString(permission, "Permission name", 256)

  private def validateString(value: String, name: String, maxLength: Int)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    if value.trim.isEmpty then ctx.validation(s"$name cannot be empty").invalidNel
    else if value.length > maxLength then
      ctx.validation(s"$name cannot exceed $maxLength characters").invalidNel
    else ().validNel

  private def validatePattern(pattern: String, context: String)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    if pattern.trim.isEmpty then ctx.validation(s"${context.capitalize} pattern cannot be empty").invalidNel
    else if pattern.length > 256 then
      ctx.validation(s"${context.capitalize} pattern cannot exceed 256 characters").invalidNel
    else ().validNel

  private def validatePathPattern(path: String)(using ctx: ApplicationContext): ValidationResult[Unit] =
    if path.trim.isEmpty then ctx.validation("Path pattern cannot be empty").invalidNel
    else if path.length > 1024 then ctx.validation("Path pattern cannot exceed 1024 characters").invalidNel
    else ().validNel

  private def validateEnvKey(key: String)(using ctx: ApplicationContext): ValidationResult[Unit] =
    if key.trim.isEmpty then ctx.validation("Environment variable key cannot be empty").invalidNel
    else if key.length > 256 then
      ctx.validation("Environment variable key cannot exceed 256 characters").invalidNel
    else if !key.matches("^[A-Za-z_][A-Za-z0-9_]*$") then
      ctx.validation("Environment variable key must match pattern [A-Za-z_][A-Za-z0-9_]*").invalidNel
    else ().validNel

  private def validateEnvValue(value: String)(using ctx: ApplicationContext): ValidationResult[Unit] =
    if value.length > 4096 then
      ctx.validation("Environment variable value cannot exceed 4096 characters").invalidNel
    else ().validNel

  private def validateUniqueWorkflowNames(workflows: NonEmptyVector[Workflow])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    val names      = workflows.map(_.name)
    val duplicates = names.toVector.groupBy(identity).collect { case (name, list) if list.size > 1 => name }
    if duplicates.nonEmpty then
      val errors = duplicates.map(name => ctx.validation(s"Duplicate workflow name: $name")).toList
      Validated.invalid(NonEmptyList.fromListUnsafe(errors))
    else ().validNel

  private def validateStepIds(steps: NonEmptyVector[Step])(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    val ids        = collectStepIds(steps)
    val duplicates = ids.groupBy(identity).collect { case (id, list) if list.size > 1 => id }
    if duplicates.nonEmpty then
      val errors = duplicates.map(id => ctx.validation(s"Duplicate step ID: $id")).toList
      Validated.invalid(NonEmptyList.fromListUnsafe(errors))
    else ().validNel

  private def collectStepIds(steps: NonEmptyVector[Step]): Vector[String] =
    steps.toVector.flatMap(collectIds)

  private def collectIds(step: Step): Vector[String] =
    val selfId = step.getMeta.flatMap(_.id).toVector
    step match
      case Step.Composite(children) => selfId ++ children.toVector.flatMap(collectIds)
      case _                        => selfId

  private def validateLabels(labels: Set[String], context: String)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    labels.toVector.traverse_(label => validateLabel(label, context))

  private def validateLabel(label: String, context: String)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    if label.trim.isEmpty then ctx.validation(s"$context cannot be empty").invalidNel
    else if label.length > 256 then ctx.validation(s"$context cannot exceed 256 characters").invalidNel
    else if !label.matches("[a-zA-Z0-9_-]+") then
      ctx
        .validation(s"$context must contain only alphanumeric characters, underscores, and hyphens")
        .invalidNel
    else ().validNel
