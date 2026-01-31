package com.verschraenkt.ci.storage.db.codecs

import com.verschraenkt.ci.core.model.{ PipelineId, WorkflowId }
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.simpleStrListTypeMapper
import com.verschraenkt.ci.storage.db.PostgresProfile.api.*
import io.circe.{ Json, parser }

object ColumnTypes:
  given pipelineIdMapper: BaseColumnType[PipelineId] =
    MappedColumnType.base[PipelineId, String](_.value, PipelineId.apply)

  given workflowIdMapper: BaseColumnType[WorkflowId] =
    MappedColumnType.base[WorkflowId, String](
      _.value,
      WorkflowId.apply
    )

  // Instant mapper (PostgreSQL TIMESTAMPTZ)
//  given instantMapper: BaseColumnType[Instant] =
//    MappedColumnType.base[Instant, java.sql.Timestamp](
//      instant => java.sql.Timestamp.from(instant),
//      timestamp => timestamp.toInstant
//    )

  // JSONB mapper using Circe
  given jsonMapper: BaseColumnType[Json] =
    MappedColumnType.base[Json, String](
      _.noSpaces,
      str => parser.parse(str).getOrElse(Json.Null)
    )

  given stringArrayMapper: BaseColumnType[Set[String]] =
    MappedColumnType.base[Set[String], List[String]](
      _.toList,
      _.toSet
    )

  given userMapper: BaseColumnType[User] =
    MappedColumnType.base[User, String](
      _.unwrap,
      User.apply
    )
