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
package com.verschraenkt.ci.storage.db.codecs

import com.verschraenkt.ci.core.model.*
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.simpleStrListTypeMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import com.verschraenkt.ci.storage.db.codecs.Enums.*

import java.util.UUID

/** Column type mappers for Slick
  *
  * This object provides implicit column type mappings for:
  *   - Core domain identifiers (PipelineId, JobId, etc.)
  *   - PostgreSQL ENUM types
  *   - UUID v7 (time-ordered UUIDs)
  *   - JSONB with Circe
  *   - Array types
  */
object ColumnTypes:

  // ============================================================================
  // Core Domain Identifiers (String-based value classes)
  // ============================================================================

  given pipelineIdMapper: BaseColumnType[PipelineId] =
    MappedColumnType.base[PipelineId, String](_.value, PipelineId.apply)

  given workflowIdMapper: BaseColumnType[WorkflowId] =
    MappedColumnType.base[WorkflowId, String](_.value, WorkflowId.apply)

  given jobIdMapper: BaseColumnType[JobId] =
    MappedColumnType.base[JobId, String](_.value, JobId.apply)

  given stepIdMapper: BaseColumnType[StepId] =
    MappedColumnType.base[StepId, String](_.value, StepId.apply)

  // ============================================================================
  // UUID-based Identifiers
  // ============================================================================

  /** UUID v7 mapper (time-ordered UUIDs)
    *
    * Note: The database uses uuid_generate_v7() function for default values. This mapper just handles the
    * UUID type.
    */
  given uuidMapper: BaseColumnType[UUID] =
    MappedColumnType.base[UUID, UUID](identity, identity)

  // ============================================================================
  // PostgreSQL ENUM Type Mappers
  // ============================================================================

  given executionStatusMapper: BaseColumnType[ExecutionStatus] =
    MappedColumnType.base[ExecutionStatus, String](
      _.toDbString,
      ExecutionStatus.fromString
    )

  given triggerTypeMapper: BaseColumnType[TriggerType] =
    MappedColumnType.base[TriggerType, String](
      _.toDbString,
      TriggerType.fromString
    )

  given stepTypeMapper: BaseColumnType[StepType] =
    MappedColumnType.base[StepType, String](
      _.toDbString,
      StepType.fromString
    )

  given secretScopeMapper: BaseColumnType[SecretScope] =
    MappedColumnType.base[SecretScope, String](
      _.toDbString,
      SecretScope.fromString
    )

  given storageBackendMapper: BaseColumnType[StorageBackend] =
    MappedColumnType.base[StorageBackend, String](
      _.toDbString,
      StorageBackend.fromString
    )

  given executorStatusMapper: BaseColumnType[ExecutorStatus] =
    MappedColumnType.base[ExecutorStatus, String](
      _.toDbString,
      ExecutorStatus.fromString
    )

  given cacheStatusMapper: BaseColumnType[CacheStatus] =
    MappedColumnType.base[CacheStatus, String](
      _.toDbString,
      CacheStatus.fromString
    )

  given cacheScopeTypeMapper: BaseColumnType[CacheScopeType] =
    MappedColumnType.base[CacheScopeType, String](
      _.toDbString,
      CacheScopeType.fromString
    )

  given actorTypeMapper: BaseColumnType[ActorType] =
    MappedColumnType.base[ActorType, String](
      _.toDbString,
      ActorType.fromString
    )

  // ============================================================================
  // JSONB Mapper (using Circe)
  // ============================================================================

  /** JSONB column mapper using Circe
    *
    * Note: JSON/JSONB support is provided by PgCirceJsonSupport trait mixed into PostgresProfile. The
    * CirceJsonImplicits in MyAPI provides the proper column type mapping for io.circe.Json. This maps to
    * PostgreSQL's JSONB type (not string/varchar).
    */

  // ============================================================================
  // Array Type Mappers
  // ============================================================================

  /** String array mapper (PostgreSQL TEXT[])
    *
    * Maps Set[String] to PostgreSQL TEXT[] array type using slick-pg's built-in List[String] support.
    */
  given stringSetMapper: BaseColumnType[Set[String]] =
    MappedColumnType.base[Set[String], List[String]](
      set => set.toList,
      list => list.toSet
    )

  // ============================================================================
  // User Type Mapper (temporary)
  // ============================================================================

  given userMapper: BaseColumnType[User] =
    MappedColumnType.base[User, String](
      _.unwrap,
      User.apply
    )

  // Note: Instant mapper is provided by PgDate2Support via Date2DateTimeImplicitsDuration
  // No need to define it here as it would create ambiguous implicits
