package com.verschraenkt.ci.engine.api

import com.verschraenkt.ci.core.errors.ValidationError
import com.verschraenkt.ci.core.security.{ JwtClaims, JwtValidator }

/** Opaque type for validated JWT tokens */
opaque type JwtToken = String

object JwtToken:
  /** Create a validated JWT token
    * @param raw
    *   The raw token string
    * @param validator
    *   The JWT validator to use
    * @return
    *   Either a validation error or the validated token
    */
  def apply(raw: String)(using validator: JwtValidator): Either[ValidationError, JwtToken] =
    validator.validate(raw).map(_ => raw)

  /** Create a token without validation (unsafe - for testing only)
    * @param raw
    *   The raw token string
    * @return
    *   The unvalidated token
    */
  def unsafe(raw: String): JwtToken = raw

  /** Extension methods for JwtToken */
  extension (t: JwtToken)
    /** Get the raw token string */
    def rawValue: String = t

    /** Extract claims from the token
      * @param validator
      *   The JWT validator to use
      * @return
      *   The extracted claims
      */
    def claims(using validator: JwtValidator): Either[ValidationError, JwtClaims] =
      val payload = t.split("\\.")(1)
      validator.extractClaims(payload)
