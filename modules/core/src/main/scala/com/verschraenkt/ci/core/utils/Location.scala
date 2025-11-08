package com.verschraenkt.ci.core.utils

final case class Location(file: String, line: Option[Int] = None, col: Option[Int] = None):
  override def toString: String =
    (line, col) match
      case (Some(l), Some(c)) => s"$file:$l:$c"
      case (Some(l), None)    => s"$file:$l"
      case _                  => file
