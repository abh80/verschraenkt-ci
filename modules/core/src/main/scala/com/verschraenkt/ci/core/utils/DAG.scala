package com.verschraenkt.ci.core.utils

import com.verschraenkt.ci.core.model.{ Job, JobId }
import com.verschraenkt.ci.core.errors.{ DomainError, ValidationError }
import scala.annotation.tailrec
import scala.collection.immutable.ListMap

object DAG:
  def order(jobs: List[Job]): Either[DomainError, List[Job]] =
    val byId = ListMap.from(jobs.map(j => j.id -> j))
    if byId.size != jobs.size then
      val dupes = jobs.groupBy(_.id).collect { case (k, v) if v.size > 1 => k.value }.toList.sorted
      Left(ValidationError(s"Duplicate job ids: ${dupes.mkString(", ")}"))
    else
      val allIds  = byId.keySet
      val unknown = jobs.flatMap(_.dependencies).filterNot(allIds.contains).map(_.value).distinct.sorted
      if unknown.nonEmpty then Left(ValidationError(s"Unknown dependencies: ${unknown.mkString(", ")}"))
      else
        @tailrec
        def loop(remaining: List[Job], doneIds: Set[JobId], acc: List[Job]): Either[DomainError, List[Job]] =
          if remaining.isEmpty then Right(acc)
          else
            val (ready, blocked) = remaining.partition(j => j.dependencies.subsetOf(doneIds))
            if ready.isEmpty then Left(ValidationError("Cyclic dependency detected"))
            else
              val nextDone = doneIds ++ ready.iterator.map(_.id)
              loop(blocked, nextDone, acc ++ ready)
        loop(jobs, Set.empty, Nil)
