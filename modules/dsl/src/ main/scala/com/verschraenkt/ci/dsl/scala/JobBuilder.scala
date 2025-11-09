package com.verschraenkt.ci.dsl.scala

import com.verschraenkt.ci.core.model.*

final case class JobBuilder(
    id: JobId,
    steps: NonEmptyVector[Step],
    dependencies: Set[JobId] = Set.empty,
    resources: Resource = Resource(1000, 512, 0, 0),
    timeout: FiniteDuration,
    matrix: Map[String, NonEmptyVector[String]] = Map.empty,
    container: Option[Container],
    labels: Set[String] = Set.empty,
    concurrencyGroup: Option[String] = None,
    condition: Condition = Condition.Always
)
