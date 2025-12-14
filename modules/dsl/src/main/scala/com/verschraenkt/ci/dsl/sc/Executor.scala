package com.verschraenkt.ci.dsl.sc

import _root_.com.verschraenkt.ci.core.model.Container

sealed trait Executor:
  def toContainer: Container

object Docker:
  private[ci] final case class Exec(
      image: String,
      args: List[String] = Nil,
      env: Map[String, String] = Map.empty,
      user: Option[String] = None,
      workdir: Option[String] = None
  ) extends Executor:
    def toContainer: Container = new Container(image, args, env, user, workdir)
