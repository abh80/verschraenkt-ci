package com.verschraenkt.ci.engine.api

import java.util.UUID

// Engine-specific identifiers
// Note: PipelineId, JobId, WorkflowId, and StepId are defined in core.model package
case class ExecutionId(value: UUID)   extends AnyVal
case class ExecutorId(value: UUID)    extends AnyVal
case class CorrelationId(value: UUID) extends AnyVal
