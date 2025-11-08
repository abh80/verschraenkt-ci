package com.verschraenkt.ci.core.model

final case class Container(
    image: String,
    args: List[String] = Nil,
    env: Map[String, String] = Map.empty,
    user: Option[String] = None,
    workdir: Option[String] = None
)
