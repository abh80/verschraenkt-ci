package com.verschraenkt.ci.storage.db

import com.github.tminglei.slickpg.*
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities

/** Enhanced PostgreSQL profile with support for:
  *   - PostgreSQL ENUM types
  *   - JSONB via PgCirceJsonSupport (io.circe.Json)
  *   - Array types
  *   - java.time types (Instant, LocalDateTime, etc.)
  */
trait MyPostgresProfile
    extends ExPostgresProfile
    with PgArraySupport
    with PgEnumSupport
    with PgDate2Support
    with PgCirceJsonSupport:

  // Use JSONB type (PostgreSQL 9.4+)
  override def pgjson: String = "jsonb"

  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api = MyAPI

  object MyAPI
      extends ExtPostgresAPI
      with ArrayImplicits
      with Date2DateTimeImplicitsDuration
      with JsonImplicits

object PostgresProfile extends MyPostgresProfile
