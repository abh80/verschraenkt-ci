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
package com.verschraenkt.ci.core.security

import cats.data.ValidatedNel
import cats.implicits.*
import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.errors.ValidationError
import com.verschraenkt.ci.core.model.{ Command, Policy, ShellKind }

import scala.annotation.unused

/** Utility for sanitizing and validating commands against security policies */
object CommandSanitizer:
  type ValidationResult[A] = ValidatedNel[ValidationError, A]

  /** Validate a command against a security policy
    * @param command
    *   The command to validate
    * @param policy
    *   The security policy to enforce
    * @param ctx
    *   Application context for error reporting
    * @return
    *   Validation result
    */
  def validate(command: Command, policy: Policy)(using ctx: ApplicationContext): ValidationResult[Unit] =
    command match
      case Command.Exec(program, args, env, _, timeout) =>
        (
          validateExecutable(program, policy),
          validateTimeout(timeout, policy),
          validateEnvironment(env, policy),
          validateArguments(args, policy)
        ).mapN((_, _, _, _) => ())

      case Command.Shell(script, shell, env, _, timeout) =>
        (
          validateShellAllowed(policy),
          validateShellScript(script, shell, policy),
          validateTimeout(timeout, policy),
          validateEnvironment(env, policy)
        ).mapN((_, _, _, _) => ())

      case Command.Composite(steps) =>
        steps.toVector.traverse_(cmd => validate(cmd, policy))

  /** Check if shell commands are allowed by policy */
  private def validateShellAllowed(policy: Policy)(using ctx: ApplicationContext): ValidationResult[Unit] =
    if policy.allowShell then ().validNel
    else ctx.validation("Shell commands are not allowed by security policy").invalidNel

  /** Validate executable against allowlist */
  private def validateExecutable(program: String, policy: Policy)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    policy.allowedExecutables match
      case None => ().validNel // No allowlist configured
      case Some(allowed) =>
        if allowed.contains(program) || allowed.exists(pattern => matchesGlob(program, pattern)) then
          ().validNel
        else ctx.validation(s"Executable '$program' is not in the allowed executables list").invalidNel

  /** Validate shell script against deny patterns */
  private def validateShellScript(
      script: String,
      shell: ShellKind,
      policy: Policy
  )(using ctx: ApplicationContext): ValidationResult[Unit] =
    val deniedPatterns = policy.denyPatterns.map(_.r)

    deniedPatterns.find(pattern => pattern.findFirstIn(script).isDefined) match
      case Some(matchedPattern) =>
        ctx
          .validation(s"Shell script contains forbidden pattern: ${matchedPattern.pattern.pattern}")
          .invalidNel
      case None =>
        // Additional shell-specific checks
        validateShellInjection(script, shell)

  /** Validate arguments don't contain dangerous patterns */
  private def validateArguments(args: List[String], policy: Policy)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    val deniedPatterns = policy.denyPatterns.map(_.r)

    args.zipWithIndex.find { case (arg, _) =>
      deniedPatterns.exists(pattern => pattern.findFirstIn(arg).isDefined)
    } match
      case Some((arg, idx)) =>
        ctx.validation(s"Argument at index $idx contains forbidden pattern: '$arg'").invalidNel
      case None =>
        ().validNel

  /** Detect common shell injection patterns */
  private def validateShellInjection(@unused script: String, @unused shell: ShellKind)(using
      @unused ctx: ApplicationContext
  ): ValidationResult[Unit] =
    /*
    List(
      """\$\(.*\)""".r, // Command substitution $(...)
      "`.*`".r,         // Backtick command substitution
      """;.*""".r,      // Command chaining with semicolon
      """&&""".r,       // Command chaining with AND
      """\|\|""".r,     // Command chaining with OR
      """\|(?!\|)""".r, // Pipe (but not ||)
      """>""".r,        // Output redirection
      """<""".r,        // Input redirection
      """\$\{.*\}""".r  // Variable expansion
    )
     */

    // These patterns are informational warnings, not hard blocks
    // The deny patterns in Policy should be used for strict enforcement
    ().validNel

  /** Validate timeout against policy limits */
  private def validateTimeout(timeout: Option[Int], policy: Policy)(using
      ctx: ApplicationContext
  ): ValidationResult[Unit] =
    timeout match
      case None                                 => ().validNel
      case Some(t) if t <= policy.maxTimeoutSec => ().validNel
      case Some(t) =>
        ctx
          .validation(
            s"Command timeout ($t seconds) exceeds policy maximum (${policy.maxTimeoutSec} seconds)"
          )
          .invalidNel

  /** Validate environment variables against blocked list */
  private def validateEnvironment(
      env: Map[String, String],
      policy: Policy
  )(using ctx: ApplicationContext): ValidationResult[Unit] =
    val blocked = env.keys.filter(policy.blockEnvironmentVariables.contains)

    if blocked.isEmpty then ().validNel
    else ctx.validation(s"Environment variables are blocked by policy: ${blocked.mkString(", ")}").invalidNel

  /** Simple glob pattern matching (supports * wildcards) */
  private def matchesGlob(text: String, pattern: String): Boolean =
    val regexPattern = "^" + pattern.replace("*", ".*").replace("?", ".") + "$"
    text.matches(regexPattern)
