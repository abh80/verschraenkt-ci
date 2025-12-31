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
