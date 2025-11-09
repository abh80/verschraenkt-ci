package com.verschraenkt.ci.dsl.scala

import com.verschraenkt.ci.core.model.*

final case class JobBuilder(name: String, steps: Vector[Step]) {}
