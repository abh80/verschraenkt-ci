package com.verschraenkt.ci.core.errors

final case class ValidationError(
  reason: String,
  override val source: Option[String] = None,
  override val location: Option[(String, Int)] = None
) extends DomainError(reason, source, location)