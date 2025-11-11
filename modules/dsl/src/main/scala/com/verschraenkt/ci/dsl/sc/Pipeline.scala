package com.verschraenkt.ci.dsl.sc

import com.verschraenkt.ci.core.model.Workflow

case class Pipeline(name: String, var workflows: Seq[Workflow])
