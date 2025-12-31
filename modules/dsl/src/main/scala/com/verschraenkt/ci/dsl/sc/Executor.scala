/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
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
