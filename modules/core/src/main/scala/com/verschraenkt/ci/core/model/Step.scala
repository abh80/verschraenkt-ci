package com.verschraenkt.ci.core.model

import cats.data.{ NonEmptyList, NonEmptyVector }

import scala.concurrent.duration.FiniteDuration

/** Metadata configuration for pipeline steps
  * @param id
  *   Optional identifier for the step
  * @param when
  *   Optional condition for when this step should run
  * @param timeout
  *   Optional timeout in seconds for the step execution
  * @param continueOnError
  *   Whether to continue pipeline execution if this step fails
  * @param retry
  *   Optional number of times to retry this step if it fails
  * @param env
  *   Environment variables to set for this step
  * @param workingDirectory
  *   Optional working directory for this step
  */
final case class StepMeta(
    id: Option[String] = None,
    when: When = When.Always,
    timeout: Option[FiniteDuration] = None,
    continueOnError: Boolean = false,
    retry: Option[Retry] = None,
    env: Map[String, String] = Map.empty,
    workingDirectory: Option[String] = None
)

/** Trait for steps that have metadata */
sealed trait HasMeta:
  def meta: StepMeta

/** Pipeline execution steps */
enum Step derives CanEqual:
  /** Checkout source code from version control */
  case Checkout()(using val meta: StepMeta) extends Step with HasMeta

  /** Run a command */
  case Run(command: CommandLike)(using val meta: StepMeta) extends Step with HasMeta

  /** Restore files from cache */
  case RestoreCache(cache: CacheLike, paths: NonEmptyList[String])(using val meta: StepMeta)
      extends Step
      with HasMeta

  /** Save files to cache */
  case SaveCache(cache: CacheLike, paths: NonEmptyList[String])(using val meta: StepMeta)
      extends Step
      with HasMeta

  /** Composite step containing multiple steps */
  case Composite(steps: NonEmptyVector[Step]) extends Step

extension (step: Step)
  /** Get the metadata for this step if it has any */
  def getMeta: Option[StepMeta] = step match
    case h: HasMeta => Some(h.meta)
    case _          => None

  /** Create a copy of this step with modified metadata */
  def withMeta(f: StepMeta => StepMeta): Step = step match
    case s: HasMeta =>
      s match
        case c: Step.Checkout      => c.copy()(using f(c.meta))
        case r: Step.Run           => r.copy()(using f(r.meta))
        case rc: Step.RestoreCache => rc.copy()(using f(rc.meta))
        case sc: Step.SaveCache    => sc.copy()(using f(sc.meta))
    case c: Step.Composite => c

enum When:
  case Always, OnSuccess, OnFailure

enum RetryMode:
  case Linear, Exponential

final case class Retry(maxAttempts: Int, delay: FiniteDuration, mode: RetryMode = RetryMode.Exponential)
