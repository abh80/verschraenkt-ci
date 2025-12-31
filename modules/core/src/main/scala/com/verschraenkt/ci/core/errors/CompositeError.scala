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
