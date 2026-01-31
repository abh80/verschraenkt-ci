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

import cats.implicits.*
import munit.FunSuite

class CommandSpec extends FunSuite:

  test("Exec command creation with default values") {
    val cmd: Command.Exec = Command.Exec("echo")
    assertEquals(cmd.program, "echo")
    assertEquals(cmd.args, Nil)
    assertEquals(cmd.env, Map.empty)
    assertEquals(cmd.cwd, None)
    assertEquals(cmd.timeoutSec, None)
  }

  test("Exec command creation with all parameters") {
    val cmd: Command.Exec = Command.Exec(
      program = "npm",
      args = List("install", "--production"),
      env = Map("NODE_ENV" -> "production"),
      cwd = Some("/app"),
      timeoutSec = Some(300)
    )
    assertEquals(cmd.program, "npm")
    assertEquals(cmd.args, List("install", "--production"))
    assertEquals(cmd.env, Map("NODE_ENV" -> "production"))
    assertEquals(cmd.cwd, Some("/app"))
    assertEquals(cmd.timeoutSec, Some(300))
  }

  test("Shell command creation with default values") {
    val cmd: Command.Shell = Command.Shell("echo hello")
    assertEquals(cmd.script, "echo hello")
    assertEquals(cmd.shell, ShellKind.Bash)
    assertEquals(cmd.env, Map.empty)
    assertEquals(cmd.cwd, None)
    assertEquals(cmd.timeoutSec, None)
  }

  test("Shell command with different shell types") {
    val bash: Command.Shell = Command.Shell("ls -la", ShellKind.Bash)
    assertEquals(bash.shell, ShellKind.Bash)

    val sh: Command.Shell = Command.Shell("ls -la", ShellKind.Sh)
    assertEquals(sh.shell, ShellKind.Sh)

    val pwsh: Command.Shell = Command.Shell("Get-ChildItem", ShellKind.Pwsh)
    assertEquals(pwsh.shell, ShellKind.Pwsh)

    val cmd: Command.Shell = Command.Shell("dir", ShellKind.Cmd)
    assertEquals(cmd.shell, ShellKind.Cmd)
  }

  test("Command asCommand returns itself") {
    val exec = Command.Exec("echo")
    assertEquals(exec.asCommand, exec)

    val shell = Command.Shell("echo hello")
    assertEquals(shell.asCommand, shell)
  }

  test("Composite.one creates single command composite") {
    val cmd       = Command.Exec("echo")
    val composite = Composite.one(cmd)
    assertEquals(composite.steps.length, 1)
    assertEquals(composite.steps.head, cmd)
  }

  test("Composite.two creates two command composite") {
    val cmd1      = Command.Exec("echo", List("hello"))
    val cmd2      = Command.Exec("echo", List("world"))
    val composite = Composite.two(cmd1, cmd2)
    assertEquals(composite.steps.length, 2)
    assertEquals(composite.steps.head, cmd1)
    assertEquals(composite.steps.tail.head, cmd2)
  }

  test("Composite asCommand converts to Command.Composite") {
    val cmd1      = Command.Exec("echo")
    val composite = Composite.one(cmd1)
    val command   = composite.asCommand
    assert(command.isInstanceOf[Command.Composite])
    command match
      case Command.Composite(steps) => assertEquals(steps.length, 1)
      case _                        => fail("Expected Command.Composite")
  }

  test("~> operator chains two commands") {
    val cmd1      = Command.Exec("npm", List("install"))
    val cmd2      = Command.Exec("npm", List("test"))
    val composite = cmd1 ~> cmd2
    assertEquals(composite.steps.length, 2)
    assertEquals(composite.steps.toVector, Vector(cmd1, cmd2))
  }

  test("~> operator chains command with composite") {
    val cmd1       = Command.Exec("npm", List("install"))
    val cmd2       = Command.Exec("npm", List("test"))
    val cmd3       = Command.Exec("npm", List("build"))
    val composite1 = cmd2 ~> cmd3
    val result     = cmd1 ~> composite1
    assertEquals(result.steps.length, 3)
    assertEquals(result.steps.toVector, Vector(cmd1, cmd2, cmd3))
  }

  test("~> operator chains composite with command") {
    val cmd1      = Command.Exec("npm", List("install"))
    val cmd2      = Command.Exec("npm", List("test"))
    val cmd3      = Command.Exec("npm", List("build"))
    val composite = cmd1 ~> cmd2
    val result    = composite ~> cmd3
    assertEquals(result.steps.length, 3)
    assertEquals(result.steps.toVector, Vector(cmd1, cmd2, cmd3))
  }

  test("~> operator chains two composites") {
    val cmd1       = Command.Exec("npm", List("install"))
    val cmd2       = Command.Exec("npm", List("test"))
    val cmd3       = Command.Exec("npm", List("build"))
    val cmd4       = Command.Exec("npm", List("deploy"))
    val composite1 = cmd1 ~> cmd2
    val composite2 = cmd3 ~> cmd4
    val result     = composite1 ~> composite2
    assertEquals(result.steps.length, 4)
    assertEquals(result.steps.toVector, Vector(cmd1, cmd2, cmd3, cmd4))
  }

  test("~> operator chains multiple commands at once") {
    val cmd1      = Command.Exec("echo", List("1"))
    val cmd2      = Command.Exec("echo", List("2"))
    val cmd3      = Command.Exec("echo", List("3"))
    val cmd4      = Command.Exec("echo", List("4"))
    val composite = cmd1 ~> (cmd2, cmd3, cmd4)
    assertEquals(composite.steps.length, 4)
    assertEquals(composite.steps.toVector, Vector(cmd1, cmd2, cmd3, cmd4))
  }

  test("Semigroup combine concatenates composite steps") {
    val cmd1       = Command.Exec("echo", List("1"))
    val cmd2       = Command.Exec("echo", List("2"))
    val cmd3       = Command.Exec("echo", List("3"))
    val composite1 = Composite.one(cmd1)
    val composite2 = Composite.two(cmd2, cmd3)
    val result     = composite1 |+| composite2
    assertEquals(result.steps.length, 3)
    assertEquals(result.steps.toVector, Vector(cmd1, cmd2, cmd3))
  }

  test("Semigroup is associative") {
    val cmd1 = Command.Exec("echo", List("1"))
    val cmd2 = Command.Exec("echo", List("2"))
    val cmd3 = Command.Exec("echo", List("3"))
    val c1   = Composite.one(cmd1)
    val c2   = Composite.one(cmd2)
    val c3   = Composite.one(cmd3)

    val left  = (c1 |+| c2) |+| c3
    val right = c1 |+| (c2 |+| c3)

    assertEquals(left.steps.toVector, right.steps.toVector)
  }

  test("complex command chain with mixed operators") {
    val install = Command.Exec("npm", List("install"))
    val test    = Command.Shell("npm test && npm run coverage")
    val build   = Command.Exec("npm", List("run", "build"))
    val deploy  = Command.Shell("./deploy.sh", ShellKind.Bash)

    val pipeline = install ~> test ~> build ~> deploy
    assertEquals(pipeline.steps.length, 4)
    assertEquals(pipeline.steps.toVector, Vector(install, test, build, deploy))
  }

  test("Policy creation") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 600,
      denyPatterns = List("rm -rf", "format c:")
    )
    assertEquals(policy.allowShell, true)
    assertEquals(policy.maxTimeoutSec, 600)
    assertEquals(policy.denyPatterns.length, 2)
  }

  test("CommandResult creation") {
    val result = CommandResult(
      exitCode = 0,
      stdout = Vector("Hello", "World"),
      stderr = Vector.empty,
      startedAt = 1000L,
      endedAt = 2000L
    )
    assertEquals(result.exitCode, 0)
    assertEquals(result.stdout, Vector("Hello", "World"))
    assertEquals(result.stderr, Vector.empty)
    assertEquals(result.endedAt - result.startedAt, 1000L)
  }

  test("CommandResult with error output") {
    val result = CommandResult(
      exitCode = 1,
      stdout = Vector.empty,
      stderr = Vector("Error: file not found"),
      startedAt = 1000L,
      endedAt = 1500L
    )
    assertEquals(result.exitCode, 1)
    assert(result.stderr.nonEmpty)
  }

  test("Composite can be converted to Command.Composite") {
    val cmd1      = Command.Exec("echo")
    val cmd2      = Command.Shell("pwd")
    val composite = cmd1 ~> cmd2
    val command   = composite.asCommand

    command match
      case Command.Composite(steps) =>
        assertEquals(steps.length, 2)
        assertEquals(steps.head, cmd1)
        assertEquals(steps.tail.head, cmd2)
      case _ => fail("Expected Command.Composite")
  }

  test("empty environment map for commands") {
    val exec: Command.Exec   = Command.Exec("ls")
    val shell: Command.Shell = Command.Shell("ls")
    assert(exec.env.isEmpty)
    assert(shell.env.isEmpty)
  }

  test("command with environment variables") {
    val env = Map(
      "NODE_ENV" -> "production",
      "PORT"     -> "3000",
      "DEBUG"    -> "true"
    )
    val cmd: Command.Exec = Command.Exec("node", List("server.js"), env = env)
    assertEquals(cmd.env.size, 3)
    assertEquals(cmd.env("NODE_ENV"), "production")
    assertEquals(cmd.env("PORT"), "3000")
    assertEquals(cmd.env("DEBUG"), "true")
  }

  test("chaining preserves command properties") {
    val cmd1 = Command.Exec(
      "npm",
      List("install"),
      env = Map("NODE_ENV" -> "dev"),
      cwd = Some("/app"),
      timeoutSec = Some(300)
    )
    val cmd2      = Command.Exec("npm", List("test"))
    val composite = cmd1 ~> cmd2

    val first = composite.steps.head
    first match
      case Command.Exec(program, args, env, cwd, timeout) =>
        assertEquals(program, "npm")
        assertEquals(args, List("install"))
        assertEquals(env, Map("NODE_ENV" -> "dev"))
        assertEquals(cwd, Some("/app"))
        assertEquals(timeout, Some(300))
      case _ => fail("Expected Exec command")
  }
  test("~> operator throws IllegalArgumentException for non-CommandLike elements in tuple") {
    val cmd1 = Command.Exec("echo")
    intercept[IllegalArgumentException] {
      cmd1 ~> ("not a command", 123)
    }
  }
