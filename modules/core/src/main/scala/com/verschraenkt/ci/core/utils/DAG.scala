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
package com.verschraenkt.ci.core.utils

import com.verschraenkt.ci.core.context.ApplicationContext
import com.verschraenkt.ci.core.errors.DomainError
import com.verschraenkt.ci.core.model.{ Job, JobId }

import scala.annotation.tailrec
import scala.collection.immutable.ListMap

object DAG:
  def order(jobs: List[Job])(using ctx: ApplicationContext): Either[DomainError, List[Job]] =
    val byId = ListMap.from(jobs.map(j => j.id -> j))
    if byId.size != jobs.size then
      val dupes = jobs.groupBy(_.id).collect { case (k, v) if v.size > 1 => k.value }.toList.sorted
      Left(
        ctx.validation(
          s"Duplicate job ids: ${dupes.mkString(", ")}"
        )
      )
    else
      val allIds  = byId.keySet
      val unknown = jobs.flatMap(_.dependencies).filterNot(allIds.contains).map(_.value).distinct.sorted
      if unknown.nonEmpty then
        Left(
          ctx.validation(
            s"Unknown dependencies: ${unknown.mkString(", ")}"
          )
        )
      else
        @tailrec
        def loop(remaining: List[Job], doneIds: Set[JobId], acc: List[Job]): Either[DomainError, List[Job]] =
          if remaining.isEmpty then Right(acc)
          else
            val (ready, blocked) = remaining.partition(j => j.dependencies.subsetOf(doneIds))
            if ready.isEmpty then
              val cycles = remaining
                .flatMap(job => job.dependencies.map(dep => s"${job.id.value} depends on ${dep.value}"))
                .mkString(" and ")
              Left(
                ctx.validation(
                  s"Cyclic dependency detected: $cycles"
                )
              )
            else
              val nextDone = doneIds ++ ready.iterator.map(_.id)
              loop(blocked, nextDone, acc ++ ready)
        loop(jobs, Set.empty, Nil)
