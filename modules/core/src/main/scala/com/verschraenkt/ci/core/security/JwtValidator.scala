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
import scala.util.{Try, Success, Failure}
import java.util.Base64
import java.time.Instant

/** JWT token claims */
case class JwtClaims(
    issuer: Option[String],        // iss
    subject: Option[String],       // sub
    audience: Option[Set[String]], // aud
    expiresAt: Option[Long],       // exp (Unix timestamp)
    notBefore: Option[Long],       // nbf (Unix timestamp)
    issuedAt: Option[Long],        // iat (Unix timestamp)
    jwtId: Option[String],         // jti
    customClaims: Map[String, String] = Map.empty
)

/** JWT validation configuration */
case class JwtConfig(
    expectedIssuer: Option[String] = None,
    expectedAudience: Set[String] = Set.empty,
    clockSkewSeconds: Int = 60,
    requireExpiration: Boolean = true,
    requireIssuer: Boolean = false,
    requireAudience: Boolean = false
)

/** Validator for JWT tokens
  * 
  * Note: This is a basic implementation for structural validation.
  * For production use, integrate with a proper JWT library like:
  * - jwt-scala
  * - jose4j
  * - nimbus-jose-jwt
  */
class JwtValidator(config: JwtConfig):
  
  /** Validate a JWT token string
    * @param token The raw JWT token string
    * @return Either a validation error or the validated claims
    */
  def validate(token: String): Either[ValidationError, JwtClaims] =
    for
      parts <- parseToken(token)
      claims <- extractClaims(parts.payload)
      _ <- validateClaims(claims)
    yield claims

  /** Parse JWT token into header, payload, signature parts */
  private def parseToken(token: String): Either[ValidationError, TokenParts] =
    val parts = token.split("\\.")
    if parts.length != 3 then
      Left(ValidationError("Invalid JWT format: expected 3 parts separated by '.'", Some("jwt.parse")))
    else
      Right(TokenParts(parts(0), parts(1), parts(2)))

  /** Extract claims from base64-encoded payload */
  def extractClaims(payload: String): Either[ValidationError, JwtClaims] =
    decodeBase64(payload).flatMap(parseClaims)

  /** Decode base64url string */
  private def decodeBase64(encoded: String): Either[ValidationError, String] =
    Try {
      val decoder = Base64.getUrlDecoder
      new String(decoder.decode(encoded), "UTF-8")
    }.toEither.left.map(e => 
      ValidationError(s"Failed to decode base64: ${e.getMessage}", Some("jwt.decode"))
    )

  /** Parse JSON claims (simplified - in production use a JSON library) */
  private def parseClaims(json: String): Either[ValidationError, JwtClaims] =
    // This is a simplified parser. In production, use circe, play-json, or similar
    Try {
      val issuer = extractJsonString(json, "iss")
      val subject = extractJsonString(json, "sub")
      val audience = extractJsonStringOrArray(json, "aud")
      val expiresAt = extractJsonLong(json, "exp")
      val notBefore = extractJsonLong(json, "nbf")
      val issuedAt = extractJsonLong(json, "iat")
      val jwtId = extractJsonString(json, "jti")

      JwtClaims(
        issuer = issuer,
        subject = subject,
        audience = audience.map(_.toSet),
        expiresAt = expiresAt,
        notBefore = notBefore,
        issuedAt = issuedAt,
        jwtId = jwtId
      )
    }.toEither.left.map(e =>
      ValidationError(s"Failed to parse JWT claims: ${e.getMessage}", Some("jwt.claims"))
    )

  /** Validate claims against configuration */
  private def validateClaims(claims: JwtClaims): Either[ValidationError, Unit] =
    val now = Instant.now().getEpochSecond
    
    // Check expiration
    if config.requireExpiration && claims.expiresAt.isEmpty then
      return Left(ValidationError("JWT token missing required 'exp' claim", Some("jwt.validation.exp")))
    
    claims.expiresAt match
      case Some(exp) if exp + config.clockSkewSeconds < now =>
        return Left(ValidationError(s"JWT token has expired (exp=$exp, now=$now)", Some("jwt.validation.expired")))
      case _ => ()

    // Check not before
    claims.notBefore match
      case Some(nbf) if nbf - config.clockSkewSeconds > now =>
        return Left(ValidationError(s"JWT token not yet valid (nbf=$nbf, now=$now)", Some("jwt.validation.nbf")))
      case _ => ()

    // Check issuer
    if config.requireIssuer && claims.issuer.isEmpty then
      return Left(ValidationError("JWT token missing required 'iss' claim", Some("jwt.validation.iss")))
    
    config.expectedIssuer match
      case Some(expected) if claims.issuer.exists(_ != expected) =>
        return Left(ValidationError(
          s"JWT token issuer mismatch (expected=$expected, got=${claims.issuer.get})",
          Some("jwt.validation.issuer")
        ))
      case _ => ()

    // Check audience
    if config.requireAudience && claims.audience.isEmpty then
      return Left(ValidationError("JWT token missing required 'aud' claim", Some("jwt.validation.aud")))
    
    if config.expectedAudience.nonEmpty then
      claims.audience match
        case Some(aud) if !aud.exists(config.expectedAudience.contains) =>
          return Left(ValidationError(
            s"JWT token audience mismatch (expected=${config.expectedAudience.mkString(",")}, got=${aud.mkString(",")})",
            Some("jwt.validation.audience")
          ))
        case None if config.requireAudience =>
          return Left(ValidationError("JWT token missing required audience", Some("jwt.validation.audience")))
        case _ => ()

    Right(())

  /** Extract string field from JSON (simplified) */
  private def extractJsonString(json: String, field: String): Option[String] =
    val pattern = s""""$field"\\s*:\\s*"([^"]*)"""".r
    pattern.findFirstMatchIn(json).map(_.group(1))

  /** Extract long field from JSON (simplified) */
  private def extractJsonLong(json: String, field: String): Option[Long] =
    val pattern = s""""$field"\\s*:\\s*(\\d+)""".r
    pattern.findFirstMatchIn(json).flatMap(m => Try(m.group(1).toLong).toOption)

  /** Extract string or array field from JSON (simplified) */
  private def extractJsonStringOrArray(json: String, field: String): Option[List[String]] =
    // Try to match string value
    val stringPattern = s""""$field"\\s*:\\s*"([^"]*)"""".r
    stringPattern.findFirstMatchIn(json).map(m => List(m.group(1))) orElse {
      // Try to match array value
      val arrayPattern = s""""$field"\\s*:\\s*\\[([^\\]]*)\\]""".r
      arrayPattern.findFirstMatchIn(json).map { m =>
        val items = m.group(1)
        """"([^"]*)"""".r.findAllMatchIn(items).map(_.group(1)).toList
      }
    }

private case class TokenParts(header: String, payload: String, signature: String)
