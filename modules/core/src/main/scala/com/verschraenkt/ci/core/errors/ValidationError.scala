package com.verschraenkt.ci.core.errors

final case class ValidationError(reason: String) extends DomainError(reason)