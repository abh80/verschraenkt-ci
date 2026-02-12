package com.verschraenkt.ci.storage.db.codecs

import cats.data.{ NonEmptyList, NonEmptyVector }
import com.verschraenkt.ci.core.model.*
import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder, Json }

import scala.concurrent.duration.FiniteDuration

/** JSON encoders and decoders for domain models
  *
  * This object provides Circe encoders and decoders for value classes and domain types that need custom
  * serialization.
  */
object JsonCodecs:

  // Value Class Encoders/Decoders

  given pipelineIdEncoder: Encoder[PipelineId] = Encoder[String].contramap(_.value)
  given pipelineIdDecoder: Decoder[PipelineId] = Decoder[String].map(PipelineId.apply)

  given workflowIdEncoder: Encoder[WorkflowId] = Encoder[String].contramap(_.value)
  given workflowIdDecoder: Decoder[WorkflowId] = Decoder[String].map(WorkflowId.apply)

  given jobIdEncoder: Encoder[JobId] = Encoder[String].contramap(_.value)
  given jobIdDecoder: Decoder[JobId] = Decoder[String].map(JobId.apply)

  given stepIdEncoder: Encoder[StepId] = Encoder[String].contramap(_.value)
  given stepIdDecoder: Decoder[StepId] = Decoder[String].map(StepId.apply)

  given cacheKeyEncoder: Encoder[CacheKey] = Encoder[String].contramap(_.value)
  given cacheKeyDecoder: Decoder[CacheKey] = Decoder[String].map(s => CacheKey.literal(s))

  // FiniteDuration Encoder/Decoder

  given finiteDurationEncoder: Encoder[FiniteDuration] = Encoder[Long].contramap(_.toMillis)
  given finiteDurationDecoder: Decoder[FiniteDuration] =
    Decoder[Long].map(millis => FiniteDuration(millis, scala.concurrent.duration.MILLISECONDS))

  // Cats Data Structures Encoders/Decoders

  given nevEncoder[A](using enc: Encoder[A]): Encoder[NonEmptyVector[A]] =
    Encoder[Vector[A]].contramap(_.toVector)

  given nevDecoder[A](using dec: Decoder[A]): Decoder[NonEmptyVector[A]] =
    Decoder[Vector[A]].emap { vec =>
      NonEmptyVector.fromVector(vec).toRight("Cannot decode empty vector as NonEmptyVector")
    }

  given nelEncoder[A](using enc: Encoder[A]): Encoder[NonEmptyList[A]] =
    Encoder[List[A]].contramap(_.toList)

  given nelDecoder[A](using dec: Decoder[A]): Decoder[NonEmptyList[A]] =
    Decoder[List[A]].emap { list =>
      NonEmptyList.fromList(list).toRight("Cannot decode empty list as NonEmptyList")
    }

  // Enum Encoders/Decoders

  given cacheScopeEncoder: Encoder[CacheScope] = Encoder[Int].contramap(_.intVal)
  given cacheScopeDecoder: Decoder[CacheScope] =
    Decoder[Int].emap(i => CacheScope.fromInt(i).toRight(s"Invalid CacheScope value: $i"))

  given patternKindEncoder: Encoder[PatternKind] = Encoder[String].contramap(_.toString)
  given patternKindDecoder: Decoder[PatternKind] = Decoder[String].emap {
    case "Wildcard" => Right(PatternKind.Wildcard)
    case "Regex"    => Right(PatternKind.Regex)
    case "Exact"    => Right(PatternKind.Exact)
    case other      => Left(s"Invalid PatternKind: $other")
  }

  given shellKindEncoder: Encoder[ShellKind] = Encoder[String].contramap(_.toString)
  given shellKindDecoder: Decoder[ShellKind] = Decoder[String].emap {
    case "Bash" => Right(ShellKind.Bash)
    case "Sh"   => Right(ShellKind.Sh)
    case "Pwsh" => Right(ShellKind.Pwsh)
    case "Cmd"  => Right(ShellKind.Cmd)
    case other  => Left(s"Invalid ShellKind: $other")
  }

  given whenEncoder: Encoder[When] = Encoder[String].contramap(_.toString)
  given whenDecoder: Decoder[When] = Decoder[String].emap {
    case "Always"    => Right(When.Always)
    case "OnSuccess" => Right(When.OnSuccess)
    case "OnFailure" => Right(When.OnFailure)
    case "Never"     => Right(When.Never)
    case other       => Left(s"Invalid When: $other")
  }

  given retryModeEncoder: Encoder[RetryMode] = Encoder[String].contramap(_.toString)
  given retryModeDecoder: Decoder[RetryMode] = Decoder[String].emap {
    case "Linear"      => Right(RetryMode.Linear)
    case "Exponential" => Right(RetryMode.Exponential)
    case other         => Left(s"Invalid RetryMode: $other")
  }

  // Simple Case Class Encoders/Decoders (using semiauto)

  given containerEncoder: Encoder[Container] = deriveEncoder[Container]
  given containerDecoder: Decoder[Container] = deriveDecoder[Container]

  given resourceEncoder: Encoder[Resource] = deriveEncoder[Resource]
  given resourceDecoder: Decoder[Resource] = deriveDecoder[Resource]

  given retryEncoder: Encoder[Retry] = deriveEncoder[Retry]
  given retryDecoder: Decoder[Retry] = deriveDecoder[Retry]

  given stepMetaEncoder: Encoder[StepMeta] = deriveEncoder[StepMeta]
  given stepMetaDecoder: Decoder[StepMeta] = deriveDecoder[StepMeta]

  given policyEncoder: Encoder[Policy] = deriveEncoder[Policy]
  given policyDecoder: Decoder[Policy] = deriveDecoder[Policy]

  given commandResultEncoder: Encoder[CommandResult] = deriveEncoder[CommandResult]
  given commandResultDecoder: Decoder[CommandResult] = deriveDecoder[CommandResult]

  // Cache Encoders/Decoders

  given restoreCacheEncoder: Encoder[Cache.RestoreCache] = deriveEncoder[Cache.RestoreCache]
  given restoreCacheDecoder: Decoder[Cache.RestoreCache] = deriveDecoder[Cache.RestoreCache]

  given saveCacheEncoder: Encoder[Cache.SaveCache] = deriveEncoder[Cache.SaveCache]
  given saveCacheDecoder: Decoder[Cache.SaveCache] = deriveDecoder[Cache.SaveCache]

  // Command Encoders/Decoders (ADT with enum)

  given commandEncoder: Encoder[Command] = new Encoder[Command]:
    def apply(cmd: Command): Json = cmd match
      case Command.Exec(program, args, env, cwd, timeoutSec) =>
        Json.obj(
          "type"       -> Json.fromString("Exec"),
          "program"    -> Json.fromString(program),
          "args"       -> Encoder[List[String]].apply(args),
          "env"        -> Encoder[Map[String, String]].apply(env),
          "cwd"        -> Encoder[Option[String]].apply(cwd),
          "timeoutSec" -> Encoder[Option[Int]].apply(timeoutSec)
        )
      case Command.Shell(script, shell, env, cwd, timeoutSec) =>
        Json.obj(
          "type"       -> Json.fromString("Shell"),
          "script"     -> Json.fromString(script),
          "shell"      -> shellKindEncoder(shell),
          "env"        -> Encoder[Map[String, String]].apply(env),
          "cwd"        -> Encoder[Option[String]].apply(cwd),
          "timeoutSec" -> Encoder[Option[Int]].apply(timeoutSec)
        )
      case Command.Composite(steps) =>
        Json.obj(
          "type"  -> Json.fromString("Composite"),
          "steps" -> nevEncoder[Command](using this).apply(steps)
        )

  given commandDecoder: Decoder[Command] = new Decoder[Command]:
    def apply(cursor: io.circe.HCursor): io.circe.Decoder.Result[Command] =
      cursor.downField("type").as[String].flatMap {
        case "Exec" =>
          for
            program    <- cursor.downField("program").as[String]
            args       <- cursor.downField("args").as[List[String]]
            env        <- cursor.downField("env").as[Map[String, String]]
            cwd        <- cursor.downField("cwd").as[Option[String]]
            timeoutSec <- cursor.downField("timeoutSec").as[Option[Int]]
          yield Command.Exec(program, args, env, cwd, timeoutSec)
        case "Shell" =>
          for
            script     <- cursor.downField("script").as[String]
            shell      <- cursor.downField("shell").as[ShellKind]
            env        <- cursor.downField("env").as[Map[String, String]]
            cwd        <- cursor.downField("cwd").as[Option[String]]
            timeoutSec <- cursor.downField("timeoutSec").as[Option[Int]]
          yield Command.Shell(script, shell, env, cwd, timeoutSec)
        case "Composite" =>
          for steps <- cursor
              .downField("steps")
              .as[NonEmptyVector[Command]](using nevDecoder[Command](using this))
          yield Command.Composite(steps)
        case other =>
          Left(io.circe.DecodingFailure(s"Invalid Command type: $other", cursor.history))
      }

  // Condition Encoders/Decoders (large ADT with enum)

  given conditionEncoder: Encoder[Condition] = deriveEncoder[Condition]
  given conditionDecoder: Decoder[Condition] = deriveDecoder[Condition]

  // Step Encoders/Decoders (ADT with enum)

  given stepEncoder: Encoder[Step] = new Encoder[Step]:
    def apply(step: Step): Json = step match
      case s: Step.Checkout =>
        Json.obj(
          "type" -> Json.fromString("Checkout"),
          "meta" -> stepMetaEncoder(s.meta)
        )
      case s: Step.Run =>
        Json.obj(
          "type"    -> Json.fromString("Run"),
          "command" -> commandEncoder(s.command.asCommand),
          "meta"    -> stepMetaEncoder(s.meta)
        )
      case s: Step.RestoreCache =>
        Json.obj(
          "type" -> Json.fromString("RestoreCache"),
          "cache" -> Json.obj(
            "key"   -> cacheKeyEncoder(s.cache.key),
            "scope" -> cacheScopeEncoder(s.cache.scope)
          ),
          "paths" -> nelEncoder[String].apply(s.paths),
          "meta"  -> stepMetaEncoder(s.meta)
        )
      case s: Step.SaveCache =>
        Json.obj(
          "type" -> Json.fromString("SaveCache"),
          "cache" -> Json.obj(
            "key"   -> cacheKeyEncoder(s.cache.key),
            "scope" -> cacheScopeEncoder(s.cache.scope)
          ),
          "paths" -> nelEncoder[String].apply(s.paths),
          "meta"  -> stepMetaEncoder(s.meta)
        )
      case Step.Composite(steps) =>
        Json.obj(
          "type"  -> Json.fromString("Composite"),
          "steps" -> nevEncoder[Step](using this).apply(steps)
        )

  given stepDecoder: Decoder[Step] = new Decoder[Step]:
    def apply(cursor: io.circe.HCursor): io.circe.Decoder.Result[Step] =
      cursor.downField("type").as[String].flatMap {
        case "Checkout" =>
          for meta <- cursor.downField("meta").as[StepMeta]
          yield Step.Checkout()(using meta)
        case "Run" =>
          for
            command <- cursor.downField("command").as[Command]
            meta    <- cursor.downField("meta").as[StepMeta]
          yield Step.Run(command)(using meta)
        case "RestoreCache" =>
          for
            cacheKey <- cursor.downField("cache").downField("key").as[CacheKey]
            scope    <- cursor.downField("cache").downField("scope").as[CacheScope]
            paths    <- cursor.downField("paths").as[NonEmptyList[String]]
            meta     <- cursor.downField("meta").as[StepMeta]
            cache = Cache.RestoreCache(cacheKey, paths, scope)
          yield Step.RestoreCache(cache, paths)(using meta)
        case "SaveCache" =>
          for
            cacheKey <- cursor.downField("cache").downField("key").as[CacheKey]
            scope    <- cursor.downField("cache").downField("scope").as[CacheScope]
            paths    <- cursor.downField("paths").as[NonEmptyList[String]]
            meta     <- cursor.downField("meta").as[StepMeta]
            cache = Cache.SaveCache(cacheKey, paths, scope)
          yield Step.SaveCache(cache, paths)(using meta)
        case "Composite" =>
          for steps <- cursor
              .downField("steps")
              .as[NonEmptyVector[Step]](using nevDecoder[Step](using this))
          yield Step.Composite(steps)
        case other =>
          Left(io.circe.DecodingFailure(s"Invalid Step type: $other", cursor.history))
      }

  // Job Encoder/Decoder

  given jobEncoder: Encoder[Job] = deriveEncoder[Job]
  given jobDecoder: Decoder[Job] = deriveDecoder[Job]

  // Workflow Encoder/Decoder

  given workflowEncoder: Encoder[Workflow] = deriveEncoder[Workflow]
  given workflowDecoder: Decoder[Workflow] = deriveDecoder[Workflow]

  // Pipeline Encoder/Decoder

  given pipelineEncoder: Encoder[Pipeline] = deriveEncoder[Pipeline]
  given pipelineDecoder: Decoder[Pipeline] = deriveDecoder[Pipeline]
