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
package com.verschraenkt.ci.dsl.sc

import _root_.com.verschraenkt.ci.core.model.*
import cats.data.NonEmptyList

import scala.concurrent.duration.FiniteDuration

type Meta = StepMetaBuilder => StepMetaBuilder

final class MetaBuilder extends (StepMetaBuilder => StepMetaBuilder):
  var meta: Meta = identity

  def withMeta[A](f: Meta)(block: => A): A =
    val old = meta
    meta = meta.andThen(f)
    try
      block
    finally
      meta = old

  def apply(b: StepMetaBuilder): StepMetaBuilder = meta(b)

sealed trait StepLike

object StepLike:

  final case class Run(
      shellCommand: String,
      shell: ShellKind = ShellKind.Sh,
      f: Meta = identity
  ) extends StepLike

  case class RestoreCache(cache: CacheLike, paths: NonEmptyList[String]) extends StepLike

  case class SaveCache(cache: CacheLike, paths: NonEmptyList[String]) extends StepLike

  case object Checkout extends StepLike

final class StepsBuilder:
  private[sc] val stepMeta = MetaBuilder()
  private var acc          = Vector.empty[StepLike]

  def add(step: StepLike): Unit = synchronized { acc :+= step }

  def modifyLast(f: StepLike => StepLike): Unit = synchronized {
    if acc.nonEmpty then
      val last = acc.last
      acc = acc.dropRight(1) :+ f(last)
  }

  def steps(body: StepsBuilder ?=> Unit): Vector[StepLike] =
    given sb: StepsBuilder = StepsBuilder()
    body
    sb.result

  def result: Vector[StepLike] = synchronized { acc }

class AddedStep(sb: StepsBuilder):
  def withMeta(f: Meta): AddedStep =
    sb.modifyLast {
      case r: StepLike.Run => r.copy(f = r.f.andThen(f))
      case other           => other
    }
    this

object StepBuilder:
  def toStep(step: StepLike)(using meta: StepMeta, sb: StepsBuilder): Step =
    step match
      case r: StepLike.Run =>
        val f              = sb.stepMeta.andThen(r.f)
        val newMetaBuilder = f(StepMetaBuilder.from(meta))
        val finalMeta      = newMetaBuilder.toStepMeta
        val command = Command.Shell(
          r.shellCommand,
          r.shell,
          finalMeta.env,
          finalMeta.workingDirectory,
          finalMeta.timeout.map(_.toSeconds.toInt)
        )
        Step.Run(command)(using finalMeta)

      case StepLike.Checkout        => Step.Checkout()
      case r: StepLike.RestoreCache => Step.RestoreCache(r.cache, r.paths)
      case r: StepLike.SaveCache    => Step.SaveCache(r.cache, r.paths)

case class StepMetaBuilder(
    id: Option[String] = None,
    when: When = When.Always,
    timeout: Option[FiniteDuration] = None,
    continueOnError: Boolean = false,
    retry: Option[Retry] = None,
    env: Map[String, String] = Map.empty,
    workingDirectory: Option[String] = None
):
  def timeout(duration: FiniteDuration): StepMetaBuilder = this.copy(timeout = Some(duration))
  def continueOnError(should: Boolean): StepMetaBuilder  = this.copy(continueOnError = should)
  def when(_when: When): StepMetaBuilder                 = this.copy(when = _when)
  def retry(mode: Retry): StepMetaBuilder                = this.copy(retry = Some(mode))
  def env(vars: (String, String)*): StepMetaBuilder      = this.copy(env = this.env ++ vars.toMap)
  def workingDir(dir: String): StepMetaBuilder           = this.copy(workingDirectory = Some(dir))

  def toStepMeta: StepMeta =
    StepMeta(id, when, timeout, continueOnError, retry, env, workingDirectory)

object StepMetaBuilder:
  def from(meta: StepMeta): StepMetaBuilder =
    new StepMetaBuilder(
      meta.id,
      meta.when,
      meta.timeout,
      meta.continueOnError,
      meta.retry,
      meta.env,
      meta.workingDirectory
    )
