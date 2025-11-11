package com.verschraenkt.ci.dsl.sc

object dsl:
  extension (n: Int) def GB: Long = (n.toLong * 953.674316).toLong // in MiB
