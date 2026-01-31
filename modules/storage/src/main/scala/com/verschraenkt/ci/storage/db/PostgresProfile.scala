package com.verschraenkt.ci.storage.db

import com.github.tminglei.slickpg.*

trait MyPostgresProfile extends ExPostgresProfile with PgArraySupport:

  override val api = MyAPI

  object MyAPI extends ExtPostgresAPI with ArrayImplicits

object PostgresProfile extends MyPostgresProfile
