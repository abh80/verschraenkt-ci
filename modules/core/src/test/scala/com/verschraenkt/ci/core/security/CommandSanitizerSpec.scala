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

import munit.FunSuite
import com.verschraenkt.ci.core.model.{Command, Policy}
import com.verschraenkt.ci.core.context.ApplicationContext

class CommandSanitizerSpec extends FunSuite:

  given ApplicationContext = ApplicationContext("test")

  private val permissivePolicy = Policy(
    allowShell = true,
    maxTimeoutSec = 3600,
    denyPatterns = List.empty
  )

  private val restrictivePolicy = Policy(
    allowShell = false,
    maxTimeoutSec = 300,
    denyPatterns = List("rm -rf /", "format c:"),
    allowedExecutables = Some(Set("npm", "node", "git")),
    blockEnvironmentVariables = Set("AWS_SECRET_ACCESS_KEY", "GITHUB_TOKEN")
  )

  test("validate - allows exec command with permissive policy") {
    val cmd = Command.Exec("echo", List("hello"))
    val result = CommandSanitizer.validate(cmd, permissivePolicy)
    assert(result.isValid)
  }

  test("validate - allows shell command when shell is permitted") {
    val cmd = Command.Shell("echo hello")
    val result = CommandSanitizer.validate(cmd, permissivePolicy)
    assert(result.isValid)
  }

  test("validate - rejects shell command when shell is not allowed") {
    val cmd = Command.Shell("echo hello")
    val result = CommandSanitizer.validate(cmd, restrictivePolicy)
    assert(result.isInvalid)
    val errors = result.swap.toOption.get.toList
    assert(errors.exists(_.message.contains("Shell commands are not allowed")))
  }

  test("validate - rejects command matching deny pattern") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 3600,
      denyPatterns = List("rm -rf /")
    )
    val cmd = Command.Shell("rm -rf /")
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isInvalid)
    val errors = result.swap.toOption.get.toList
    assert(errors.exists(_.message.contains("forbidden pattern")))
  }

  test("validate - rejects exec with non-allowed executable") {
    val cmd = Command.Exec("dangerous-tool", List("--destroy"))
    val result = CommandSanitizer.validate(cmd, restrictivePolicy)
    assert(result.isInvalid)
    val errors = result.swap.toOption.get.toList
    assert(errors.exists(_.message.contains("not in the allowed executables")))
  }

  test("validate - allows exec with allowed executable") {
    val policy = restrictivePolicy.copy(allowShell = true) // need shell for this test to pass
    val cmd = Command.Exec("npm", List("install"))
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isValid)
  }

  test("validate - rejects command exceeding timeout limit") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 60,
      denyPatterns = List.empty
    )
    val cmd = Command.Exec("long-running", timeoutSec = Some(120))
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isInvalid)
    val errors = result.swap.toOption.get.toList
    assert(errors.exists(_.message.contains("exceeds policy maximum")))
  }

  test("validate - allows command within timeout limit") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 300,
      denyPatterns = List.empty
    )
    val cmd = Command.Exec("fast-command", timeoutSec = Some(60))
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isValid)
  }

  test("validate - rejects blocked environment variables") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 3600,
      denyPatterns = List.empty,
      blockEnvironmentVariables = Set("SECRET_KEY")
    )
    val cmd = Command.Exec("echo", env = Map("SECRET_KEY" -> "value"))
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isInvalid)
    val errors = result.swap.toOption.get.toList
    assert(errors.exists(_.message.contains("blocked by policy")))
  }

  test("validate - allows non-blocked environment variables") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 3600,
      denyPatterns = List.empty,
      blockEnvironmentVariables = Set("SECRET_KEY")
    )
    val cmd = Command.Exec("echo", env = Map("PUBLIC_KEY" -> "value"))
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isValid)
  }

  test("validate - validates composite commands") {
    val cmd = Command.Composite(
      cats.data.NonEmptyVector.of(
        Command.Exec("npm", List("install")),
        Command.Exec("npm", List("test"))
      )
    )
    val result = CommandSanitizer.validate(cmd, permissivePolicy)
    assert(result.isValid)
  }

  test("validate - rejects composite if any command fails validation") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 3600,
      denyPatterns = List("rm -rf")
    )
    val cmd = Command.Composite(
      cats.data.NonEmptyVector.of(
        Command.Exec("npm", List("install")),
        Command.Shell("rm -rf /tmp/test")
      )
    )
    val result = CommandSanitizer.validate(cmd, policy)
    assert(result.isInvalid)
  }

  test("Policy default values") {
    val policy = Policy(
      allowShell = true,
      maxTimeoutSec = 3600,
      denyPatterns = List.empty
    )
    assertEquals(policy.allowedExecutables, None)
    assertEquals(policy.blockEnvironmentVariables, Set.empty)
    assertEquals(policy.maxOutputBytes, 10L * 1024 * 1024)
  }
