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

/** Utility methods for working with Step and Command types */
object StepUtils:
  /** Extract command strings from a sequence of steps
    * @param steps
    *   Sequence of steps to process
    * @return
    *   Sequence of optional command strings (None for steps that don't have commands)
    */
  def extractCommandStrings(steps: Seq[Step]): Seq[Option[String]] =
    steps.map {
      case Step.Run(cmd) =>
        cmd.asCommand match
          case Command.Exec(program, args, _, _, _) => Some((program :: args).mkString(" "))
          case Command.Shell(script, _, _, _, _)    => Some(script)
          case Command.Composite(_)                 => None
      case _ => None
    }

  /** Map over steps, transforming commands while preserving the step structure
    * @param step
    *   The step to map over
    * @param f
    *   Function to transform commands
    * @return
    *   A new step with transformed commands
    */
  def mapCommands(step: Step)(f: Command => Command): Step = step match
    case s @ Step.Run(cmd) =>
      val newCmd = cmd.asCommand match
        case c: Command => f(c)
      s.copy(command = newCmd)(using s.meta)
    case s: Step.Composite =>
      s.copy(steps = s.steps.map(mapCommands(_)(f)))
    case other => other

  /** Find all commands in a step, including those in composite steps
    * @param step
    *   The step to search in
    * @return
    *   Sequence of all commands found
    */
  def findAllCommands(step: Step): Seq[Command] = step match
    case Step.Run(cmd) => Seq(cmd.asCommand)
    case Step.Composite(steps) =>
      steps.toVector.flatMap(findAllCommands)
    case _ => Nil

  /** Find all shell commands in a step
    * @param step
    *   The step to search in
    * @return
    *   Sequence of shell command strings
    */
  def findAllShellCommands(step: Step): Seq[String] =
    findAllCommands(step).collect { case Command.Shell(script, _, _, _, _) =>
      script
    }

  /** Find all exec commands in a step
    * @param step
    *   The step to search in
    * @return
    *   Sequence of (program, args) tuples
    */
  def findAllExecCommands(step: Step): Seq[(String, List[String])] =
    findAllCommands(step).collect { case Command.Exec(program, args, _, _, _) =>
      (program, args)
    }
