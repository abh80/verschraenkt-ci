package com.verschraenkt.ci.dsl.scala

object dsl:
  extension (n: Int) def GB: Long = n.toLong * 953.674316 // in MiB
