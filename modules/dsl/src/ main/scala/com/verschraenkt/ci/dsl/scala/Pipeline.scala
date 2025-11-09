package com.verschraenkt.ci.dsl.scala

case class Pipeline(name: String, var workflows: Seq[Workflow])
