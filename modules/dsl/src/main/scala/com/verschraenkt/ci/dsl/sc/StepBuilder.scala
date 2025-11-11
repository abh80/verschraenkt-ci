package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyList
import com.verschraenkt.ci.core.model.{ CacheLike, Command, Retry, ShellKind, Step, StepMeta, When }

import scala.concurrent.duration.FiniteDuration

sealed trait StepLike

object StepLike:

  final class Run(
      val shellCommand: String,
      val shell: ShellKind,
      val f: StepMetaBuilder => StepMetaBuilder
  ) extends StepLike

  object Run:
    def apply(
        shellCommand: String,
        shell: ShellKind = ShellKind.Sh
    )(f: StepMetaBuilder => StepMetaBuilder = identity): Run =
      new Run(shellCommand, shell, f)

  case object Checkout extends StepLike

  case class RestoreCache(cache: CacheLike, paths: NonEmptyList[String]) extends StepLike
  case class SaveCache(cache: CacheLike, paths: NonEmptyList[String])    extends StepLike

final class StepsBuilder:
  private var acc                   = Vector.empty[StepLike]
  def unary_+(step: StepLike): Unit = acc :+= step
  def result: Vector[StepLike]      = acc

  def steps(body: StepsBuilder ?=> Unit): Vector[StepLike] =
    given sb: StepsBuilder = StepsBuilder()
    body
    sb.result

object StepBuilder:
  def toStep(step: StepLike)(using meta: StepMeta): Step =
    step match
      case r: StepLike.Run =>
        val newMeta = r.f(StepMetaBuilder.from(meta))

        Step.Run(
          Command.Shell(
            r.shellCommand,
            r.shell,
            newMeta.env,
            newMeta.workingDirectory,
            newMeta.timeout.map(_.toSeconds.toInt)
          )
        )

      case StepLike.Checkout        => Step.Checkout()
      case r: StepLike.RestoreCache => Step.RestoreCache(r.cache, r.paths)
      case r: StepLike.SaveCache    => Step.SaveCache(r.cache, r.paths)

case class StepMetaBuilder(
    name: Option[String] = None,
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
  def env(vars: (String, String)*): StepMetaBuilder      = this.copy(env = vars.toMap())
  def workingDir(dir: String): StepMetaBuilder           = this.copy(workingDirectory = Some(dir))

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
