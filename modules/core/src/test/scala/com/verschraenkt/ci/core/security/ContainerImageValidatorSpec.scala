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

class ContainerImageValidatorSpec extends FunSuite:

  test("parseImage - valid image with registry, tag, and digest") {
    val result = ContainerImageValidator.parseImage(
      "ghcr.io/myorg/myimage:v1.0.0@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
    )
    assert(result.isRight)
    val ref = result.toOption.get
    assertEquals(ref.registry, "ghcr.io")
    assertEquals(ref.repository, "myorg/myimage")
    assertEquals(ref.tag, "v1.0.0")
    assert(ref.digest.isDefined)
  }

  test("parseImage - image without registry defaults to docker.io") {
    val result = ContainerImageValidator.parseImage("ubuntu:22.04")
    assert(result.isRight)
    val ref = result.toOption.get
    assertEquals(ref.registry, "docker.io")
    assertEquals(ref.repository, "ubuntu")
    assertEquals(ref.tag, "22.04")
  }

  test("parseImage - image without tag defaults to latest") {
    val result = ContainerImageValidator.parseImage("nginx")
    assert(result.isRight)
    val ref = result.toOption.get
    assertEquals(ref.tag, "latest")
  }

  test("validate - rejects image from unapproved registry") {
    val config = ContainerImageConfig(
      approvedRegistries = Set("ghcr.io"),
      requireDigest = false,
      allowLatestTag = true
    )
    val result = ContainerImageValidator.validate("evilregistry.io/malware:latest", config)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("not in the approved"))
  }

  test("validate - accepts image from approved registry") {
    val config = ContainerImageConfig(
      approvedRegistries = Set("ghcr.io", "gcr.io"),
      requireDigest = false,
      allowLatestTag = true
    )
    val result = ContainerImageValidator.validate("ghcr.io/myorg/myimage:v1.0", config)
    assert(result.isRight)
  }

  test("validate - rejects latest tag when not allowed") {
    val config = ContainerImageConfig(
      approvedRegistries = Set.empty,
      requireDigest = false,
      allowLatestTag = false
    )
    val result = ContainerImageValidator.validate("ghcr.io/myorg/myimage:latest", config)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains(":latest"))
  }

  test("validate - accepts latest tag when allowed") {
    val config = ContainerImageConfig(
      approvedRegistries = Set.empty,
      requireDigest = false,
      allowLatestTag = true
    )
    val result = ContainerImageValidator.validate("ghcr.io/myorg/myimage:latest", config)
    assert(result.isRight)
  }

  test("validate - rejects image without digest when required") {
    val config = ContainerImageConfig(
      approvedRegistries = Set.empty,
      requireDigest = true,
      allowLatestTag = true
    )
    val result = ContainerImageValidator.validate("ghcr.io/myorg/myimage:v1.0", config)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("digest"))
  }

  test("validate - accepts image with digest when required") {
    val config = ContainerImageConfig(
      approvedRegistries = Set.empty,
      requireDigest = true,
      allowLatestTag = true
    )
    val result = ContainerImageValidator.validate(
      "ghcr.io/myorg/myimage:v1.0@sha256:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
      config
    )
    assert(result.isRight)
  }

  test("validate - rejects path traversal in repository") {
    val config = ContainerImageValidator.lenientConfig
    val result = ContainerImageValidator.validate("ghcr.io/../../../etc/passwd:latest", config)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("path traversal"))
  }

  test("lenientConfig allows all registries and latest tag") {
    val config = ContainerImageValidator.lenientConfig
    assertEquals(config.approvedRegistries, Set.empty)
    assertEquals(config.requireDigest, false)
    assertEquals(config.allowLatestTag, true)
  }

  test("strictConfig requires digest and disallows latest") {
    val config = ContainerImageValidator.strictConfig(Set("ghcr.io"))
    assertEquals(config.approvedRegistries, Set("ghcr.io"))
    assertEquals(config.requireDigest, true)
    assertEquals(config.allowLatestTag, false)
  }
