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

import cats.Semigroup
import cats.data.NonEmptyVector
import cats.implicits.catsSyntaxSemigroup

/** Represents a command that can be executed */
sealed trait CommandLike:
  /** Converts this CommandLike into a Command */
  def asCommand: Command

/** Represents different types of executable commands */
enum Command extends CommandLike:
  /** Execute a program with arguments and environment variables
    * @param program
    *   The program to execute
    * @param args
    *   Command line arguments
    * @param env
    *   Environment variables to set
    * @param cwd
    *   Working directory for execution
    * @param timeoutSec
    *   Timeout in seconds
    */
  case Exec(
      program: String,
      args: List[String] = Nil,
      env: Map[String, String] = Map.empty,
      cwd: Option[String] = None,
      timeoutSec: Option[Int] = None
  ) extends Command

  /** Execute a shell script
    * @param script
    *   The script to execute
    * @param shell
    *   The shell to use
    * @param env
    *   Environment variables to set
    * @param cwd
    *   Working directory for execution
    * @param timeoutSec
    *   Timeout in seconds
    */
  case Shell(
      script: String,
      shell: ShellKind = ShellKind.Bash,
      env: Map[String, String] = Map.empty,
      cwd: Option[String] = None,
      timeoutSec: Option[Int] = None
  ) extends Command

  override def asCommand: Command = this

  /** A composite command made up of multiple commands executed sequentially */
  case Composite(steps: NonEmptyVector[Command]) extends Command

/** Supported shell types */
enum ShellKind:
  case Bash, Sh, Pwsh, Cmd

/** Execution policy for commands
  * @param allowShell
  *   Whether shell commands are allowed
  * @param maxTimeoutSec
  *   Maximum allowed timeout in seconds
  * @param denyPatterns
  *   List of denied command patterns (regex)
  * @param allowedExecutables
  *   Optional allowlist of permitted executables (supports glob patterns)
  * @param blockEnvironmentVariables
  *   Set of environment variable names that are blocked
  * @param maxOutputBytes
  *   Maximum allowed output size in bytes
  */
final case class Policy(
    allowShell: Boolean,
    maxTimeoutSec: Int,
    denyPatterns: List[String],
    allowedExecutables: Option[Set[String]] = None,
    blockEnvironmentVariables: Set[String] = Set.empty,
    maxOutputBytes: Long = 10L * 1024 * 1024 // 10MB default
)

/** Result of executing a command
  * @param exitCode
  *   Process exit code
  * @param stdout
  *   Standard output lines
  * @param stderr
  *   Standard error lines
  * @param startedAt
  *   Timestamp when command started
  * @param endedAt
  *   Timestamp when command completed
  */
final case class CommandResult(
    exitCode: Int,
    stdout: Vector[String],
    stderr: Vector[String],
    startedAt: Long,
    endedAt: Long
)

/** Composite command that runs multiple commands sequentially */
final case class Composite(steps: NonEmptyVector[Command]) extends CommandLike:
  def asCommand: Command = Command.Composite(steps)

/** Factory methods for creating composite commands */
object Composite:
  /** Create a composite of a single command */
  def one(c: CommandLike): Composite = Composite(NonEmptyVector.one(c.asCommand))

  /** Create a composite of two commands */
  def two(c1: CommandLike, c2: CommandLike): Composite = Composite(
    NonEmptyVector.of(c1.asCommand, c2.asCommand)
  )

/** Semigroup instance for combining composite commands */
given Semigroup[Composite] with
  override def combine(x: Composite, y: Composite): Composite = Composite(
    NonEmptyVector.fromVectorUnsafe(x.steps.toVector ++ y.steps.toVector)
  )

/** Extension methods for chaining commands */
extension (c: CommandLike)
  /** Chain two commands together */
  infix def ~>(next: CommandLike): Composite = (c, next) match
    case (comp: Composite, n) => comp |+| Composite.one(n)
    case (cmd, n)             => Composite.two(cmd, n)

  /** Chain a command with a composite */
  infix def ~>(next: Composite): Composite = c match
    case comp: Composite => comp |+| next
    case cmd             => Composite.one(cmd) |+| next

  /** Chain multiple commands together using tuple syntax */
  infix def ~>(commands: Tuple): Composite =
    val commandList = commands.toList.map {
      case cmd: CommandLike => cmd
      case other => throw new IllegalArgumentException(s"Element '$other' in tuple is not a CommandLike")
    }
    val allCommands = c +: commandList
    Composite(NonEmptyVector.fromVectorUnsafe(allCommands.toVector.map(_.asCommand)))

extension (left: Step)
  infix def ~>(right: Step): Step.Composite = (left, right) match
    case (Step.Composite(leftSteps), Step.Composite(rightSteps)) =>
      Step.Composite(leftSteps ++ rightSteps.toVector)
    case (Step.Composite(leftSteps), _) =>
      Step.Composite(leftSteps :+ right)
    case (_, Step.Composite(rightSteps)) =>
      Step.Composite(left +: rightSteps)
    case _ =>
      Step.Composite(NonEmptyVector.of(left, right))
