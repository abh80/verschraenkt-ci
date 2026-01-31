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
package com.verschraenkt.ci.core.config

import com.verschraenkt.ci.core.model.Policy
import com.verschraenkt.ci.core.security.{ ContainerImageConfig, JwtConfig }

/** Security configuration for the CI system */
case class SecurityConfig(
    jwt: JwtConfig,
    containerImages: ContainerImageConfig,
    commands: CommandSecurityConfig
)

/** Configuration for command security policies */
case class CommandSecurityConfig(
    defaultPolicy: Policy,
    allowPolicyOverride: Boolean = false
)

object SecurityConfig:
  /** Create a default security configuration suitable for development */
  def development: SecurityConfig =
    SecurityConfig(
      jwt = JwtConfig(
        expectedIssuer = Some("verschraenkt-ci-dev"),
        expectedAudience = Set("ci-executor"),
        clockSkewSeconds = 60,
        requireExpiration = true,
        requireIssuer = false,
        requireAudience = false
      ),
      containerImages = ContainerImageConfig(
        approvedRegistries = Set("docker.io", "ghcr.io", "gcr.io", "quay.io"),
        requireDigest = false,
        allowLatestTag = true,
        allowUnspecifiedRegistry = true
      ),
      commands = CommandSecurityConfig(
        defaultPolicy = Policy(
          allowShell = true,
          maxTimeoutSec = 86400,
          denyPatterns = List.empty,
          allowedExecutables = None,
          blockEnvironmentVariables = Set.empty
        ),
        allowPolicyOverride = true
      )
    )

  /** Create a strict security configuration suitable for production */
  def production: SecurityConfig =
    SecurityConfig(
      jwt = JwtConfig(
        expectedIssuer = Some("verschraenkt-ci"),
        expectedAudience = Set("ci-executor", "ci-api"),
        clockSkewSeconds = 30,
        requireExpiration = true,
        requireIssuer = true,
        requireAudience = true
      ),
      containerImages = ContainerImageConfig(
        approvedRegistries = Set("ghcr.io", "gcr.io", "quay.io"),
        requireDigest = true,
        allowLatestTag = false,
        allowUnspecifiedRegistry = false
      ),
      commands = CommandSecurityConfig(
        defaultPolicy = Policy(
          allowShell = true,
          maxTimeoutSec = 86400,
          denyPatterns = List(
            "rm -rf /",
            // Fork bomb pattern removed due to escaping issues
            "dd if=/dev/zero",
            "mkfs",
            "/dev/sd[a-z]" // Direct disk access
          ),
          allowedExecutables = None,
          blockEnvironmentVariables = Set(
            "AWS_SECRET_ACCESS_KEY",
            "AWS_SESSION_TOKEN",
            "GITHUB_TOKEN",
            "GITLAB_TOKEN",
            "NPM_TOKEN"
          )
        ),
        allowPolicyOverride = false
      )
    )
