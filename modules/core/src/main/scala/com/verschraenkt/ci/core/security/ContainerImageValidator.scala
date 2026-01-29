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

import com.verschraenkt.ci.core.errors.ValidationError
import scala.util.matching.Regex

/** Parsed container image reference */
case class ImageReference(
    registry: String,
    repository: String,
    tag: String,
    digest: Option[String]
):
  /** Reconstruct the full image reference */
  def fullReference: String =
    val base = s"$registry/$repository:$tag"
    digest.map(d => s"$base@$d").getOrElse(base)

/** Configuration for container image validation */
case class ContainerImageConfig(
    approvedRegistries: Set[String] = Set(
      "docker.io",
      "ghcr.io",
      "gcr.io",
      "quay.io"
    ),
    requireDigest: Boolean = true,
    allowLatestTag: Boolean = false,
    allowUnspecifiedRegistry: Boolean = false
)

/** Utility for validating container images */
object ContainerImageValidator:
  
  // Pattern: [registry/]repository[:tag][@digest]
  private val ImagePattern: Regex = 
    """^(?:([a-z0-9.-]+(?::[0-9]+)?)/)?([a-z0-9._/-]+)(?::([a-z0-9._-]+))?(?:@(sha256:[a-f0-9]{64}))?$""".r

  /** Parse a container image reference
    * @param image The image string to parse
    * @return Either an error or the parsed image reference
    */
  def parseImage(image: String): Either[ValidationError, ImageReference] =
    image.trim match
      case ImagePattern(registry, repository, tag, digest) =>
        val actualRegistry = Option(registry).getOrElse("docker.io")
        val actualTag = Option(tag).getOrElse("latest")
        val actualDigest = Option(digest)
        
        Right(ImageReference(
          registry = actualRegistry,
          repository = repository,
          tag = actualTag,
          digest = actualDigest
        ))
      case _ =>
        Left(ValidationError(
          s"Invalid container image format: '$image'",
          Some("container.image.format")
        ))

  /** Validate a container image against security policy
    * @param image The image string to validate
    * @param config The validation configuration
    * @return Either an error or the parsed image reference
    */
  def validate(image: String, config: ContainerImageConfig): Either[ValidationError, ImageReference] =
    for
      ref <- parseImage(image)
      _ <- validateRegistry(ref, config)
      _ <- validateTag(ref, config)
      _ <- validateDigest(ref, config)
      _ <- validateImageName(ref)
    yield ref

  /** Validate the registry is in the approved list */
  private def validateRegistry(
      ref: ImageReference,
      config: ContainerImageConfig
  ): Either[ValidationError, Unit] =
    if config.approvedRegistries.isEmpty then
      // No restrictions
      Right(())
    else if config.approvedRegistries.contains(ref.registry) then
      Right(())
    else if ref.registry == "docker.io" && !config.allowUnspecifiedRegistry then
      Left(ValidationError(
        s"Container registry '${ref.registry}' is not in the approved registries list: ${config.approvedRegistries.mkString(", ")}",
        Some("container.image.registry")
      ))
    else
      Left(ValidationError(
        s"Container registry '${ref.registry}' is not in the approved registries list: ${config.approvedRegistries.mkString(", ")}",
        Some("container.image.registry")
      ))

  /** Validate the tag (reject :latest if configured) */
  private def validateTag(
      ref: ImageReference,
      config: ContainerImageConfig
  ): Either[ValidationError, Unit] =
    if !config.allowLatestTag && ref.tag == "latest" then
      Left(ValidationError(
        "Container image tag ':latest' is not allowed for security reasons. Please specify an exact version.",
        Some("container.image.tag")
      ))
    else if ref.tag.isEmpty then
      Left(ValidationError(
        "Container image tag cannot be empty",
        Some("container.image.tag")
      ))
    else
      Right(())

  /** Validate the digest is present if required */
  private def validateDigest(
      ref: ImageReference,
      config: ContainerImageConfig
  ): Either[ValidationError, Unit] =
    if config.requireDigest && ref.digest.isEmpty then
      Left(ValidationError(
        s"Container image must include a digest (sha256:...) for security reasons. Example: ${ref.registry}/${ref.repository}:${ref.tag}@sha256:...",
        Some("container.image.digest")
      ))
    else if ref.digest.exists(!_.startsWith("sha256:")) then
      Left(ValidationError(
        "Container image digest must use sha256 algorithm",
        Some("container.image.digest")
      ))
    else if ref.digest.exists(d => d.length != 71) then // "sha256:" + 64 hex chars
      Left(ValidationError(
        "Container image digest must be a valid sha256 hash",
        Some("container.image.digest")
      ))
    else
      Right(())

  /** Validate the image name doesn't contain dangerous patterns */
  private def validateImageName(ref: ImageReference): Either[ValidationError, Unit] =
    // Check for path traversal attempts
    if ref.repository.contains("..") then
      Left(ValidationError(
        "Container image repository contains path traversal pattern",
        Some("container.image.security")
      ))
    // Check for suspicious characters
    else if ref.repository.exists(c => !c.isLetterOrDigit && c != '/' && c != '-' && c != '_' && c != '.') then
      Left(ValidationError(
        "Container image repository contains invalid characters",
        Some("container.image.security")
      ))
    else
      Right(())

  /** Create a lenient configuration (useful for development) */
  def lenientConfig: ContainerImageConfig =
    ContainerImageConfig(
      approvedRegistries = Set.empty, // Allow all
      requireDigest = false,
      allowLatestTag = true,
      allowUnspecifiedRegistry = true
    )

  /** Create a strict configuration (recommended for production) */
  def strictConfig(registries: Set[String]): ContainerImageConfig =
    ContainerImageConfig(
      approvedRegistries = registries,
      requireDigest = true,
      allowLatestTag = false,
      allowUnspecifiedRegistry = false
    )
