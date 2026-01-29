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
import java.util.Base64
import java.time.Instant

class JwtValidatorSpec extends FunSuite:

  // Helper to create a simple JWT payload (base64url encoded)
  private def encodePayload(json: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(json.getBytes("UTF-8"))

  // Helper to create a minimal valid JWT structure
  private def createJwt(payload: String): String =
    val header = encodePayload("""{"alg":"HS256","typ":"JWT"}""")
    val sig = "signature"
    s"$header.$payload.$sig"

  test("validate - rejects token with invalid format (not 3 parts)") {
    val validator = JwtValidator(JwtConfig())
    val result = validator.validate("invalid.token")
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("3 parts"))
  }

  test("validate - parses valid token claims") {
    val now = Instant.now().getEpochSecond + 3600 // 1 hour from now
    val payload = encodePayload(s"""{"iss":"test-issuer","sub":"user123","exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(requireExpiration = true))
    val result = validator.validate(token)
    assert(result.isRight)
    val claims = result.toOption.get
    assertEquals(claims.issuer, Some("test-issuer"))
    assertEquals(claims.subject, Some("user123"))
  }

  test("validate - rejects expired token") {
    val past = Instant.now().getEpochSecond - 3600 // 1 hour ago
    val payload = encodePayload(s"""{"exp":$past}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(requireExpiration = true))
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("expired"))
  }

  test("validate - accepts token within clock skew") {
    val slightlyPast = Instant.now().getEpochSecond - 30 // 30 seconds ago
    val payload = encodePayload(s"""{"exp":$slightlyPast}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(clockSkewSeconds = 60))
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("validate - rejects token not yet valid (nbf in future)") {
    val future = Instant.now().getEpochSecond + 3600
    val farFuture = future + 7200
    val payload = encodePayload(s"""{"nbf":$future,"exp":$farFuture}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig())
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("not yet valid"))
  }

  test("validate - rejects token with wrong issuer") {
    val now = Instant.now().getEpochSecond + 3600
    val payload = encodePayload(s"""{"iss":"wrong-issuer","exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(expectedIssuer = Some("correct-issuer")))
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("issuer mismatch"))
  }

  test("validate - accepts token with correct issuer") {
    val now = Instant.now().getEpochSecond + 3600
    val payload = encodePayload(s"""{"iss":"correct-issuer","exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(expectedIssuer = Some("correct-issuer")))
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("validate - rejects token without required audience") {
    val now = Instant.now().getEpochSecond + 3600
    val payload = encodePayload(s"""{"exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(requireAudience = true))
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("aud"))
  }

  test("validate - accepts token with matching audience") {
    val now = Instant.now().getEpochSecond + 3600
    val payload = encodePayload(s"""{"aud":"ci-executor","exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(
      expectedAudience = Set("ci-executor", "ci-api"),
      requireAudience = true
    ))
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("validate - rejects token with non-matching audience") {
    val now = Instant.now().getEpochSecond + 3600
    val payload = encodePayload(s"""{"aud":"wrong-audience","exp":$now}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(
      expectedAudience = Set("ci-executor"),
      requireAudience = true
    ))
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("audience mismatch"))
  }

  test("validate - rejects token without expiration when required") {
    val payload = encodePayload("""{"iss":"test"}""")
    val token = createJwt(payload)
    
    val validator = JwtValidator(JwtConfig(requireExpiration = true))
    val result = validator.validate(token)
    assert(result.isLeft)
    assert(result.swap.toOption.get.message.contains("exp"))
  }

  test("JwtConfig default values") {
    val config = JwtConfig()
    assertEquals(config.clockSkewSeconds, 60)
    assertEquals(config.requireExpiration, true)
    assertEquals(config.requireIssuer, false)
    assertEquals(config.requireAudience, false)
  }
