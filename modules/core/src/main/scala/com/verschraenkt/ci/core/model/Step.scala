package com.verschraenkt.ci.core.model

import cats.data.{ NonEmptyList, NonEmptyVector }

final case class StepMeta(
    id: Option[String] = None,
    when: Option[String] = None,
    timeoutSec: Option[Int] = None,
    continueOnError: Boolean = false,
    retry: Option[Int] = None,
    env: Map[String, String] = Map.empty,
    workingDirectory: Option[String] = None
)

sealed trait HasMeta:
  def meta: StepMeta

enum Step derives CanEqual:
  case Checkout()(using val meta: StepMeta)                extends Step with HasMeta
  case Run(command: CommandLike)(using val meta: StepMeta) extends Step with HasMeta
  case RestoreCache(cache: CacheLike, paths: NonEmptyList[String])(using val meta: StepMeta)
      extends Step
      with HasMeta
  case SaveCache(cache: CacheLike, paths: NonEmptyList[String])(using val meta: StepMeta)
      extends Step
      with HasMeta
  case Composite(steps: NonEmptyVector[Step]) extends Step

extension (step: Step)
  def getMeta: Option[StepMeta] = step match
    case h: HasMeta => Some(h.meta)
    case _          => None

  def withMeta(f: StepMeta => StepMeta): Step = step match
    case s: HasMeta =>
      s match
        case c: Step.Checkout      => c.copy()(using f(c.meta))
        case r: Step.Run           => r.copy()(using f(r.meta))
        case rc: Step.RestoreCache => rc.copy()(using f(rc.meta))
        case sc: Step.SaveCache    => sc.copy()(using f(sc.meta))
    case c: Step.Composite => c

