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

import java.util.UUID

// Engine-specific identifiers
// Note: PipelineId, JobId, WorkflowId, and StepId are defined in core.model package
case class ExecutionId(value: UUID)   extends AnyVal
case class ExecutorId(value: UUID)    extends AnyVal
case class CorrelationId(value: UUID) extends AnyVal
