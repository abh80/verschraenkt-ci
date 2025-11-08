package com.verschraenkt.ci.core.errors

import com.verschraenkt.ci.core.utils.Location

final case class ValidationError(
    override val message: String,
    override val source: Option[String] = None,
    override val location: Option[Location] = None,
    override val causeOpt: Option[Throwable] = None
) extends DomainError(message, source, location, causeOpt):
  def withSource(s: String): ValidationError     = copy(source = Some(s))
  def withLocation(l: Location): ValidationError = copy(location = Some(l))
  def withCause(t: Throwable): ValidationError   = copy(causeOpt = Some(t))
  def withMessage(m: String): ValidationError    = copy(message = m, source, location)
