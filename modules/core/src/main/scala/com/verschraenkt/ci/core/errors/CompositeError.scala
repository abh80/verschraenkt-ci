package com.verschraenkt.ci.core.errors

import com.verschraenkt.ci.core.utils.Location

final case class CompositeError(errors: Vector[DomainError])
  extends DomainError("multiple errors", None, None, None):
  def withSource(s: String): CompositeError =
    copy(errors = errors.map(_.withSource(s)))

  def withLocation(l: Location): CompositeError =
    copy(errors = errors.map(_.withLocation(l)))

  def withCause(t: Throwable): CompositeError =
    copy(errors = errors.map(_.withCause(t)))

  def withMessage(m: String): CompositeError = this

  override def toString: String =
    val rendered = errors.map(_.toString).mkString("\n  - ", "\n  - ", "")
    s"CompositeError:$rendered"