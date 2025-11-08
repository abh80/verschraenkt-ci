package com.verschraenkt.ci.core.errors

import scala.util.control.NoStackTrace

abstract class DomainError(
  val message: String,
  val source: Option[String] = None,
  val location: Option[(String, Int)] = None
) extends Exception(message) with NoStackTrace {
  
  override def toString: String = {
    val locationInfo = location.map { case (file, line) => s" at $file:$line" }.getOrElse("")
    val sourceInfo = source.map(s => s" (source: $s)").getOrElse("")
    s"${getClass.getSimpleName}: $message$locationInfo$sourceInfo"
  }
}