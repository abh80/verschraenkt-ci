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

  // Condition Encoders/Decoders (recursive ADT)
  // NOTE: Manual codecs avoid a Scala 3.8.1 staging crash in semiauto derivation for recursive enums.

  private lazy val conditionEncoderImpl: Encoder[Condition] = Encoder.instance {
    case Condition.Always => Json.obj("type" -> Json.fromString("Always"))
    case Condition.Never  => Json.obj("type" -> Json.fromString("Never"))
    case Condition.OnBranch(pattern, kind) =>
      Json.obj(
        "type"    -> Json.fromString("OnBranch"),
        "pattern" -> Json.fromString(pattern),
        "kind"    -> patternKindEncoder(kind)
      )
    case Condition.NotOnBranch(pattern, kind) =>
      Json.obj(
        "type"    -> Json.fromString("NotOnBranch"),
        "pattern" -> Json.fromString(pattern),
        "kind"    -> patternKindEncoder(kind)
      )
    case Condition.OnEvent(event) =>
      Json.obj(
        "type"  -> Json.fromString("OnEvent"),
        "event" -> Json.fromString(event)
      )
    case Condition.NotOnEvent(event) =>
      Json.obj(
        "type"  -> Json.fromString("NotOnEvent"),
        "event" -> Json.fromString(event)
      )
    case Condition.OnTag(pattern, kind) =>
      Json.obj(
        "type"    -> Json.fromString("OnTag"),
        "pattern" -> Json.fromString(pattern),
        "kind"    -> patternKindEncoder(kind)
      )
    case Condition.NotOnTag(pattern, kind) =>
      Json.obj(
        "type"    -> Json.fromString("NotOnTag"),
        "pattern" -> Json.fromString(pattern),
        "kind"    -> patternKindEncoder(kind)
      )
    case Condition.OnPathsChanged(paths) =>
      Json.obj(
        "type"  -> Json.fromString("OnPathsChanged"),
        "paths" -> nevEncoder[String].apply(paths)
      )
    case Condition.OnPathsNotChanged(paths) =>
      Json.obj(
        "type"  -> Json.fromString("OnPathsNotChanged"),
        "paths" -> nevEncoder[String].apply(paths)
      )
    case Condition.OnSuccess   => Json.obj("type" -> Json.fromString("OnSuccess"))
    case Condition.OnFailure   => Json.obj("type" -> Json.fromString("OnFailure"))
    case Condition.OnCancelled => Json.obj("type" -> Json.fromString("OnCancelled"))
    case Condition.EnvEquals(key, value) =>
      Json.obj(
        "type"  -> Json.fromString("EnvEquals"),
        "key"   -> Json.fromString(key),
        "value" -> Json.fromString(value)
      )
    case Condition.EnvNotEquals(key, value) =>
      Json.obj(
        "type"  -> Json.fromString("EnvNotEquals"),
        "key"   -> Json.fromString(key),
        "value" -> Json.fromString(value)
      )
    case Condition.EnvExists(key) =>
      Json.obj(
        "type" -> Json.fromString("EnvExists"),
        "key"  -> Json.fromString(key)
      )
    case Condition.EnvNotExists(key) =>
      Json.obj(
        "type" -> Json.fromString("EnvNotExists"),
        "key"  -> Json.fromString(key)
      )
    case Condition.OnCommitMessage(pattern, kind) =>
      Json.obj(
        "type"    -> Json.fromString("OnCommitMessage"),
        "pattern" -> Json.fromString(pattern),
        "kind"    -> patternKindEncoder(kind)
      )
    case Condition.OnAuthor(author) =>
      Json.obj(
        "type"   -> Json.fromString("OnAuthor"),
        "author" -> Json.fromString(author)
      )
    case Condition.NotOnAuthor(author) =>
      Json.obj(
        "type"   -> Json.fromString("NotOnAuthor"),
        "author" -> Json.fromString(author)
      )
    case Condition.OnPullRequest => Json.obj("type" -> Json.fromString("OnPullRequest"))
    case Condition.OnDraft       => Json.obj("type" -> Json.fromString("OnDraft"))
    case Condition.NotOnDraft    => Json.obj("type" -> Json.fromString("NotOnDraft"))
    case Condition.OnSchedule(cron) =>
      Json.obj("type" -> Json.fromString("OnSchedule"), "cron" -> Json.fromString(cron))
    case Condition.OnManualTrigger => Json.obj("type" -> Json.fromString("OnManualTrigger"))
    case Condition.OnWorkflowDispatch =>
      Json.obj("type" -> Json.fromString("OnWorkflowDispatch"))
    case Condition.OnWorkflowCall => Json.obj("type" -> Json.fromString("OnWorkflowCall"))
    case Condition.OnRepository(repo) =>
      Json.obj(
        "type" -> Json.fromString("OnRepository"),
        "repo" -> Json.fromString(repo)
      )
    case Condition.IsFork    => Json.obj("type" -> Json.fromString("IsFork"))
    case Condition.IsNotFork => Json.obj("type" -> Json.fromString("IsNotFork"))
    case Condition.IsPrivate => Json.obj("type" -> Json.fromString("IsPrivate"))
    case Condition.IsPublic  => Json.obj("type" -> Json.fromString("IsPublic"))
    case Condition.HasLabel(label) =>
      Json.obj(
        "type"  -> Json.fromString("HasLabel"),
        "label" -> Json.fromString(label)
      )
    case Condition.HasAllLabels(labels) =>
      Json.obj(
        "type"   -> Json.fromString("HasAllLabels"),
        "labels" -> nevEncoder[String].apply(labels)
      )
    case Condition.HasAnyLabel(labels) =>
      Json.obj(
        "type"   -> Json.fromString("HasAnyLabel"),
        "labels" -> nevEncoder[String].apply(labels)
      )
    case Condition.NotHasLabel(label) =>
      Json.obj(
        "type"  -> Json.fromString("NotHasLabel"),
        "label" -> Json.fromString(label)
      )
    case Condition.ActorIs(username) =>
      Json.obj(
        "type"     -> Json.fromString("ActorIs"),
        "username" -> Json.fromString(username)
      )
    case Condition.ActorIsNot(username) =>
      Json.obj(
        "type"     -> Json.fromString("ActorIsNot"),
        "username" -> Json.fromString(username)
      )
    case Condition.ActorInTeam(team) =>
      Json.obj(
        "type" -> Json.fromString("ActorInTeam"),
        "team" -> Json.fromString(team)
      )
    case Condition.HasPermission(permission) =>
      Json.obj(
        "type"       -> Json.fromString("HasPermission"),
        "permission" -> Json.fromString(permission)
      )
    case Condition.And(conditions) =>
      Json.obj(
        "type"       -> Json.fromString("And"),
        "conditions" -> nevEncoder[Condition](using conditionEncoderImpl).apply(conditions)
      )
    case Condition.Or(conditions) =>
      Json.obj(
        "type"       -> Json.fromString("Or"),
        "conditions" -> nevEncoder[Condition](using conditionEncoderImpl).apply(conditions)
      )
    case Condition.Not(condition) =>
      Json.obj(
        "type"      -> Json.fromString("Not"),
        "condition" -> conditionEncoderImpl(condition)
      )
    case Condition.Expression(expr) =>
      Json.obj(
        "type" -> Json.fromString("Expression"),
        "expr" -> Json.fromString(expr)
      )
  }

  private lazy val conditionDecoderImpl: Decoder[Condition] = Decoder.instance { cursor =>
    cursor.downField("type").as[String].flatMap {
      case "Always" => Right(Condition.Always)
      case "Never"  => Right(Condition.Never)
      case "OnBranch" =>
        for
          pattern <- cursor.downField("pattern").as[String]; kind <- cursor.downField("kind").as[PatternKind]
        yield Condition.OnBranch(pattern, kind)
      case "NotOnBranch" =>
        for
          pattern <- cursor.downField("pattern").as[String]; kind <- cursor.downField("kind").as[PatternKind]
        yield Condition.NotOnBranch(pattern, kind)
      case "OnEvent"    => cursor.downField("event").as[String].map(Condition.OnEvent.apply)
      case "NotOnEvent" => cursor.downField("event").as[String].map(Condition.NotOnEvent.apply)
      case "OnTag" =>
        for
          pattern <- cursor.downField("pattern").as[String]; kind <- cursor.downField("kind").as[PatternKind]
        yield Condition.OnTag(pattern, kind)
      case "NotOnTag" =>
        for
          pattern <- cursor.downField("pattern").as[String]; kind <- cursor.downField("kind").as[PatternKind]
        yield Condition.NotOnTag(pattern, kind)
      case "OnPathsChanged" =>
        cursor.downField("paths").as[NonEmptyVector[String]].map(Condition.OnPathsChanged.apply)
      case "OnPathsNotChanged" =>
        cursor.downField("paths").as[NonEmptyVector[String]].map(Condition.OnPathsNotChanged.apply)
      case "OnSuccess"   => Right(Condition.OnSuccess)
      case "OnFailure"   => Right(Condition.OnFailure)
      case "OnCancelled" => Right(Condition.OnCancelled)
      case "EnvEquals" =>
        for key <- cursor.downField("key").as[String]; value <- cursor.downField("value").as[String]
        yield Condition.EnvEquals(key, value)
      case "EnvNotEquals" =>
        for key <- cursor.downField("key").as[String]; value <- cursor.downField("value").as[String]
        yield Condition.EnvNotEquals(key, value)
      case "EnvExists"    => cursor.downField("key").as[String].map(Condition.EnvExists.apply)
      case "EnvNotExists" => cursor.downField("key").as[String].map(Condition.EnvNotExists.apply)
      case "OnCommitMessage" =>
        for
          pattern <- cursor.downField("pattern").as[String]; kind <- cursor.downField("kind").as[PatternKind]
        yield Condition.OnCommitMessage(pattern, kind)
      case "OnAuthor"           => cursor.downField("author").as[String].map(Condition.OnAuthor.apply)
      case "NotOnAuthor"        => cursor.downField("author").as[String].map(Condition.NotOnAuthor.apply)
      case "OnPullRequest"      => Right(Condition.OnPullRequest)
      case "OnDraft"            => Right(Condition.OnDraft)
      case "NotOnDraft"         => Right(Condition.NotOnDraft)
      case "OnSchedule"         => cursor.downField("cron").as[String].map(Condition.OnSchedule.apply)
      case "OnManualTrigger"    => Right(Condition.OnManualTrigger)
      case "OnWorkflowDispatch" => Right(Condition.OnWorkflowDispatch)
      case "OnWorkflowCall"     => Right(Condition.OnWorkflowCall)
      case "OnRepository"       => cursor.downField("repo").as[String].map(Condition.OnRepository.apply)
      case "IsFork"             => Right(Condition.IsFork)
      case "IsNotFork"          => Right(Condition.IsNotFork)
      case "IsPrivate"          => Right(Condition.IsPrivate)
      case "IsPublic"           => Right(Condition.IsPublic)
      case "HasLabel"           => cursor.downField("label").as[String].map(Condition.HasLabel.apply)
      case "HasAllLabels" =>
        cursor.downField("labels").as[NonEmptyVector[String]].map(Condition.HasAllLabels.apply)
      case "HasAnyLabel" =>
        cursor.downField("labels").as[NonEmptyVector[String]].map(Condition.HasAnyLabel.apply)
      case "NotHasLabel"   => cursor.downField("label").as[String].map(Condition.NotHasLabel.apply)
      case "ActorIs"       => cursor.downField("username").as[String].map(Condition.ActorIs.apply)
      case "ActorIsNot"    => cursor.downField("username").as[String].map(Condition.ActorIsNot.apply)
      case "ActorInTeam"   => cursor.downField("team").as[String].map(Condition.ActorInTeam.apply)
      case "HasPermission" => cursor.downField("permission").as[String].map(Condition.HasPermission.apply)
      case "And" =>
        cursor
          .downField("conditions")
          .as[NonEmptyVector[Condition]](using nevDecoder[Condition](using conditionDecoderImpl))
          .map(Condition.And.apply)
      case "Or" =>
        cursor
          .downField("conditions")
          .as[NonEmptyVector[Condition]](using nevDecoder[Condition](using conditionDecoderImpl))
          .map(Condition.Or.apply)
      case "Not" =>
        cursor
          .downField("condition")
          .as[Condition](using conditionDecoderImpl)
          .map(Condition.Not.apply)
      case "Expression" => cursor.downField("expr").as[String].map(Condition.Expression.apply)
      case other        => Left(io.circe.DecodingFailure(s"Invalid Condition type: $other", cursor.history))
    }
  }

  given conditionEncoder: Encoder[Condition] = conditionEncoderImpl
  given conditionDecoder: Decoder[Condition] = conditionDecoderImpl

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
