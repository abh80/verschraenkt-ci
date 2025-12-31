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

import scala.util.control.NoStackTrace

abstract class DomainError(
    val message: String,
    val source: Option[String],
    val location: Option[Location],
    val causeOpt: Option[Throwable]
) extends Exception(message, causeOpt.orNull)
    with NoStackTrace:
  def withSource(s: String): DomainError
  def withLocation(l: Location): DomainError
  def withCause(t: Throwable): DomainError
  def withMessage(m: String): DomainError

  override def toString: String =
    val src  = source.fold("")(s => s"[source: $s]")
    val loc  = location.fold("")(l => s" at $l")
    val base = s"$message$loc $src".trim
    causeOpt.fold(base)(c => s"$base (caused by ${c.getClass.getSimpleName}: ${c.getMessage})")
