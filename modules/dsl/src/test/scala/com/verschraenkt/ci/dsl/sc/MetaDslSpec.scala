package com.verschraenkt.ci.dsl.sc

import com.verschraenkt.ci.core.model.*
import munit.FunSuite
import scala.concurrent.duration.*

class MetaDslSpec extends FunSuite {
  import dsl.*

  test("Individual step metadata should be applied via trailing lambda") {
    val p: Pipeline = dsl.pipeline("p") {
      dsl.workflow("w") {
        dsl.job("j") {
          dsl.steps {
            dsl.run("echo 'default'")
            dsl.run("echo 'individual'").withMeta {
              _.timeout(5.minutes).continueOnError(true)
            }
          }
        }
      }
    }

    val job = p.workflows.head.jobs.head
    val steps = job.steps.toVector

    assertEquals(steps.length, 2)

    val defaultStep = steps(0).asInstanceOf[Step.Run]
    val individualStep = steps(1).asInstanceOf[Step.Run]

    assertEquals(defaultStep.meta.timeout, None)
    assertEquals(defaultStep.meta.continueOnError, false)

    assertEquals(individualStep.meta.timeout, Some(5.minutes))
    assertEquals(individualStep.meta.continueOnError, true)
  }

  test("Group step metadata should be applied via meta block") {
    val p: Pipeline = dsl.pipeline("p") {
      dsl.workflow("w") {
        dsl.job("j") {
          dsl.steps {
            dsl.meta(_.timeout(10.minutes)) {
              dsl.run("echo 'group 1'")
              dsl.run("echo 'group 2'")
            }
            dsl.run("echo 'outside group'")
          }
        }
      }
    }

    val job = p.workflows.head.jobs.head
    val steps = job.steps.toVector

    assertEquals(steps.length, 3)

    val groupStep1 = steps(0).asInstanceOf[Step.Run]
    val groupStep2 = steps(1).asInstanceOf[Step.Run]
    val outsideStep = steps(2).asInstanceOf[Step.Run]

    assertEquals(groupStep1.meta.timeout, Some(10.minutes))
    assertEquals(groupStep2.meta.timeout, Some(10.minutes))
    assertEquals(outsideStep.meta.timeout, None)
  }

  test("Individual metadata should override group metadata") {
    val p: Pipeline = dsl.pipeline("p") {
      dsl.workflow("w") {
        dsl.job("j") {
          dsl.steps {
            dsl.meta(_.timeout(10.minutes).continueOnError(true)) {
              dsl.run("echo 'group meta'")
              dsl.run("echo 'override meta'").withMeta {
                _.timeout(1.minute).continueOnError(false)
              }
            }
          }
        }
      }
    }

    val job = p.workflows.head.jobs.head
    val steps = job.steps.toVector

    assertEquals(steps.length, 2)

    val groupStep = steps(0).asInstanceOf[Step.Run]
    val overrideStep = steps(1).asInstanceOf[Step.Run]

    assertEquals(groupStep.meta.timeout, Some(10.minutes))
    assertEquals(groupStep.meta.continueOnError, true)

    assertEquals(overrideStep.meta.timeout, Some(1.minute))
    assertEquals(overrideStep.meta.continueOnError, false)
  }
}
