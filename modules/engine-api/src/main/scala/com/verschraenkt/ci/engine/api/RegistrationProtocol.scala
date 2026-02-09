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
package com.verschraenkt.ci.engine.api

// Registration Protocol
case class RegisterExecutorRequest(
    registrationToken: String,
    capabilities: ExecutorCapabilities,
    labels: Map[String, String]
)

case class RegisterExecutorResponse(
    executorId: ExecutorId,
    runnerToken: JwtToken // Validated long-lived JWT
)

case class ExecutorCapabilities(
    platform: String,           // "linux", "windows", "macos"
    architectures: Set[String], // "amd64", "arm64"
    resources: AvailableResources,
    runtimes: Set[String],    // "docker", "kubernetes", "shell"
    featureFlags: Set[String] // "gpu", "privileged", etc.
)

case class AvailableResources(
    cpu: Int,
    memory: Long, // in bytes
    disk: Long,   // in bytes
    gpu: Option[Int]
)
