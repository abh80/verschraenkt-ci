package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.*
import munit.FunSuite
import scala.concurrent.duration.*

class JobBuilderSpec extends FunSuite {

  test("JobBuilder should build a job with specified properties") {
    val jobBuilder = new JobBuilder("test-job")
    jobBuilder.addStep(StepLike.Run("echo 'hello'"))
    jobBuilder.addDependencies(JobId("dep1"), JobId("dep2"))
    jobBuilder.setResources(Resource(2000, 1024, 1, 100))
    jobBuilder.setTimeout(10.minutes)
    val container = Container("my-image")
    jobBuilder.setContainer(container)
    jobBuilder.addLabels("label1", "label2")
    jobBuilder.setConcurrencyGroup("group1")
    jobBuilder.setCondition(Condition.OnSuccess)
    val matrix = Map("os" -> NonEmptyVector.of("linux", "windows"))
    jobBuilder.setMatrix(matrix)

    val job = jobBuilder.build()

    assertEquals(job.id, JobId("test-job"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.dependencies, Set(JobId("dep1"), JobId("dep2")))
    assertEquals(job.resources, Resource(2000, 1024, 1, 100))
    assertEquals(job.timeout, 10.minutes)
    assertEquals(job.container, Some(container))
    assertEquals(job.labels, Set("label1", "label2"))
    assertEquals(job.concurrencyGroup, Some("group1"))
    assertEquals(job.condition, Condition.OnSuccess)
    assertEquals(job.matrix, matrix)
  }

  test("JobBuilder should build a job with default properties") {
    val jobBuilder = new JobBuilder("test-job-defaults")
    jobBuilder.addStep(StepLike.Run("echo 'hello'"))

    val job = jobBuilder.build()

    assertEquals(job.id, JobId("test-job-defaults"))
    assertEquals(job.steps.length, 1)
    assertEquals(job.dependencies, Set.empty[JobId])
    assertEquals(job.resources, Resource(1000, 512, 0, 0))
    assertEquals(job.timeout, 30.minutes)
    assertEquals(job.container, None)
    assertEquals(job.labels, Set.empty[String])
    assertEquals(job.concurrencyGroup, None)
    assertEquals(job.condition, Condition.Always)
    assertEquals(job.matrix, Map.empty[String, NonEmptyVector[String]])
  }

  test("JobBuilder should require at least one step") {
    val jobBuilder = new JobBuilder("no-step-job")
    intercept[IllegalArgumentException] {
      jobBuilder.build()
    }
  }

  test("JobBuilder should handle multiple steps") {
    val jobBuilder = new JobBuilder("multi-step-job")
    jobBuilder.addStep(StepLike.Run("step 1"))
    jobBuilder.addStep(StepLike.Checkout)
    jobBuilder.addStep(StepLike.Run("step 3"))

    val job = jobBuilder.build()
    assertEquals(job.steps.length, 3)
  }
}
