package com.verschraenkt.ci.dsl.sc

import cats.data.NonEmptyVector
import com.verschraenkt.ci.core.model.*
import munit.FunSuite
import scala.concurrent.duration.*

class WorkflowBuilderSpec extends FunSuite {

  private def createDummyJob(jobId: String): Job = Job.one(id = JobId(jobId), step = Step.Run(Command.Shell("echo 'dummy'"))(using StepMeta()))

  test("WorkflowBuilder should build a workflow with specified properties") {
    val workflowBuilder = new WorkflowBuilder("my-workflow")
    workflowBuilder.addJob(createDummyJob("job1"))
    val defaultContainer = Container("base-image")
    workflowBuilder.setDefaultContainer(defaultContainer)
    workflowBuilder.setConcurrency("workflow-group")
    workflowBuilder.addLabels("frontend", "dev")
    workflowBuilder.setCondition(Condition.OnFailure)

    val workflow = workflowBuilder.build()

    assertEquals(workflow.name, "my-workflow")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.defaultContainer, Some(defaultContainer))
    assertEquals(workflow.concurrencyGroup, Some("workflow-group"))
    assertEquals(workflow.labels, Set("frontend", "dev"))
    assertEquals(workflow.condition, Condition.OnFailure)
  }

  test("WorkflowBuilder should build a workflow with default properties") {
    val workflowBuilder = new WorkflowBuilder("default-workflow")
    workflowBuilder.addJob(createDummyJob("default-job"))

    val workflow = workflowBuilder.build()

    assertEquals(workflow.name, "default-workflow")
    assertEquals(workflow.jobs.length, 1)
    assertEquals(workflow.defaultContainer, None)
    assertEquals(workflow.concurrencyGroup, None)
    assertEquals(workflow.labels, Set.empty[String])
    assertEquals(workflow.condition, Condition.Always)
  }

  test("WorkflowBuilder should require at least one job") {
    val workflowBuilder = new WorkflowBuilder("empty-workflow")
    intercept[IllegalArgumentException] {
      workflowBuilder.build()
    }
  }

  test("WorkflowBuilder should handle multiple jobs") {
    val workflowBuilder = new WorkflowBuilder("multi-job-workflow")
    workflowBuilder.addJob(createDummyJob("job-a"))
    workflowBuilder.addJob(createDummyJob("job-b"))

    val workflow = workflowBuilder.build()
    assertEquals(workflow.jobs.length, 2)
  }
}
