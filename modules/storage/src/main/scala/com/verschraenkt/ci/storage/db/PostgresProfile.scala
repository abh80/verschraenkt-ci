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
package com.verschraenkt.ci.storage.db

import com.github.tminglei.slickpg.*
import com.github.tminglei.slickpg.array.PgArrayExtensions
import com.verschraenkt.ci.storage.db.codecs.Enums.{
  Architecture,
  ExecutionStatus,
  ExecutorStatus,
  Platform,
  StepType,
  TriggerType
}
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import slick.jdbc.JdbcType

/** Enhanced PostgreSQL profile with support for:
  *   - PostgreSQL ENUM types
  *   - JSONB via PgCirceJsonSupport (io.circe.Json)
  *   - Array types
  *   - java.time types (Instant, LocalDateTime, etc.)
  */
trait MyPostgresProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgArrayExtensions
    with PgEnumSupport
    with PgDate2Support
    with PgCirceJsonSupport:

  // Use JSONB type (PostgreSQL 9.4+)
  override def pgjson: String = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  private val executionStatusJdbcType: JdbcType[ExecutionStatus] = createEnumJdbcType[ExecutionStatus](
    "execution_status",
    _.toDbString,
    ExecutionStatus.fromString,
    quoteName = false
  )
  private val triggerTypeJdbcType: JdbcType[TriggerType] = createEnumJdbcType[TriggerType](
    "trigger_type",
    _.toDbString,
    TriggerType.fromString,
    quoteName = false
  )
  private val stepTypeJdbcType: JdbcType[StepType] = createEnumJdbcType[StepType](
    "step_type",
    _.toDbString,
    StepType.fromString,
    quoteName = false
  )

  private val architectureType: JdbcType[Architecture] = createEnumJdbcType[Architecture](
    "architecture_type",
    _.toDbString,
    Architecture.fromString,
    quoteName = false
  )

  private val platformType: JdbcType[Platform] = createEnumJdbcType[Platform](
    "platform_type",
    _.toDbString,
    Platform.fromString,
    quoteName = false
  )

  private val executorStatusJdbcType: JdbcType[ExecutorStatus] = createEnumJdbcType[ExecutorStatus](
    "executor_status",
    _.toDbString,
    ExecutorStatus.fromString,
    quoteName = false
  )

  override val api = MyAPI

  object MyAPI
      extends ExtPostgresAPI
      with ArrayImplicits
      with Date2DateTimeImplicitsDuration
      with JsonImplicits:
    implicit val executionStatusMapper: BaseColumnType[ExecutionStatus] =
      MyPostgresProfile.this.executionStatusJdbcType
    implicit val triggerTypeMapper: BaseColumnType[TriggerType] =
      MyPostgresProfile.this.triggerTypeJdbcType
    implicit val stepTypeMapper: BaseColumnType[StepType] =
      MyPostgresProfile.this.stepTypeJdbcType
    implicit val architectureTypeMapper: BaseColumnType[Architecture] =
      MyPostgresProfile.this.architectureType
    implicit val platformTypeMapper: BaseColumnType[Platform] = MyPostgresProfile.this.platformType
    implicit val executorStatusMapper: BaseColumnType[ExecutorStatus] =
      MyPostgresProfile.this.executorStatusJdbcType

object PostgresProfile extends MyPostgresProfile
