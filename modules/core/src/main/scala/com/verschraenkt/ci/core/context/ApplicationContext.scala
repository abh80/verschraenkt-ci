package com.verschraenkt.ci.core.context

import com.verschraenkt.ci.core.errors.ValidationError

case class ApplicationContext(source: String) {
  
  def validationError(
    message: String,
    location: Option[(String, Int)] = None
  ): ValidationError =
    ValidationError(message, Some(source), location)
  
  def validationErrorAt(
    message: String,
    file: String,
    line: Int
  ): ValidationError =
    ValidationError(message, Some(source), Some((file, line)))
}

object ApplicationContext {
  def apply(source: String): ApplicationContext = new ApplicationContext(source)
}
