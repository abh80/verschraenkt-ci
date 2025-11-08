package com.verschraenkt.ci.core.context

import com.verschraenkt.ci.core.errors.*
import com.verschraenkt.ci.core.utils.Location

final case class ApplicationContext(
    source: String,
    location: Option[Location] = None,
    meta: Map[String, String] = Map.empty,
    correlationId: Option[String] = None
):
  def at(file: String, line: Option[Int] = None, col: Option[Int] = None): ApplicationContext =
    copy(location = Some(Location(file, line, col)))

  def annotate(k: String, v: String): ApplicationContext =
    copy(meta = meta + (k -> v))

  def correlate(id: String): ApplicationContext =
    copy(correlationId = Some(id))

  def child(s: String): ApplicationContext =
    copy(source = s)

  def validation(message: String): ValidationError =
    ValidationError(message, Some(source), location)

  def validationAt(message: String, file: String, line: Int, col: Int = 0): ValidationError =
    ValidationError(
      message,
      Some(source),
      Some(Location(file, Some(line), if col == 0 then None else Some(col)))
    )

  def attach(e: DomainError): DomainError =
    e.withSource(source).withLocation(location.getOrElse(e.location.getOrElse(Location(source))))
