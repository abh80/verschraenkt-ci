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
import com.verschraenkt.ci.core.errors.*
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

class DAGSpec extends FunSuite:
  val dummyStep: Step = Step.Checkout()(using StepMeta())

  given ctx: ApplicationContext = new ApplicationContext("test.ci")

  def createJob(id: String, deps: Set[JobId] = Set.empty): Job =
    Job.one(JobId(id), dummyStep, needs = deps)

  test("DAG.order returns jobs in correct order with no dependencies") {
    val job1 = createJob("job1")
    val job2 = createJob("job2")
    val job3 = createJob("job3")

    val result = DAG.order(List(job1, job2, job3))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.length, 3)
    assertEquals(ordered.map(_.id).toSet, Set(JobId("job1"), JobId("job2"), JobId("job3")))
  }

  test("DAG.order handles simple linear dependency chain") {
    val job1 = createJob("job1")
    val job2 = createJob("job2", Set(JobId("job1")))
    val job3 = createJob("job3", Set(JobId("job2")))

    val result = DAG.order(List(job3, job1, job2))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.map(_.id), List(JobId("job1"), JobId("job2"), JobId("job3")))
  }

  test("DAG.order handles parallel jobs with common dependency") {
    val job1 = createJob("build")
    val job2 = createJob("test-unit", Set(JobId("build")))
    val job3 = createJob("test-integration", Set(JobId("build")))

    val result = DAG.order(List(job2, job3, job1))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.head.id, JobId("build"))
    assert(ordered.tail.map(_.id).toSet == Set(JobId("test-unit"), JobId("test-integration")))
  }

  test("DAG.order handles diamond dependency structure") {
    val job1 = createJob("build")
    val job2 = createJob("test", Set(JobId("build")))
    val job3 = createJob("lint", Set(JobId("build")))
    val job4 = createJob("deploy", Set(JobId("test"), JobId("lint")))

    val result = DAG.order(List(job4, job2, job3, job1))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.head.id, JobId("build"))
    assertEquals(ordered.last.id, JobId("deploy"))
  }

  test("DAG.order handles multiple independent chains") {
    val job1 = createJob("chain1-a")
    val job2 = createJob("chain1-b", Set(JobId("chain1-a")))
    val job3 = createJob("chain2-a")
    val job4 = createJob("chain2-b", Set(JobId("chain2-a")))

    val result = DAG.order(List(job2, job4, job1, job3))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.length, 4)
    val idx1a = ordered.indexWhere(_.id == JobId("chain1-a"))
    val idx1b = ordered.indexWhere(_.id == JobId("chain1-b"))
    val idx2a = ordered.indexWhere(_.id == JobId("chain2-a"))
    val idx2b = ordered.indexWhere(_.id == JobId("chain2-b"))
    assert(idx1a < idx1b)
    assert(idx2a < idx2b)
  }

  test("DAG.order detects cyclic dependency - two jobs") {
    val job1 = createJob("job1", Set(JobId("job2")))
    val job2 = createJob("job2", Set(JobId("job1")))

    val result = DAG.order(List(job1, job2))

    assert(result.isLeft)
    result.left.foreach { error =>
      assertEquals(error.message, "Cyclic dependency detected: job1 depends on job2 and job2 depends on job1")
    }
  }

  test("DAG.order detects cyclic dependency - three jobs") {
    val job1 = createJob("job1", Set(JobId("job2")))
    val job2 = createJob("job2", Set(JobId("job3")))
    val job3 = createJob("job3", Set(JobId("job1")))

    val result = DAG.order(List(job1, job2, job3))

    assert(result.isLeft)
    result.left.foreach { error =>
      assertEquals(
        error.message,
        "Cyclic dependency detected: job1 depends on job2 and job2 depends on job3 and job3 depends on job1"
      )
    }
  }

  test("DAG.order detects self-dependency") {
    val job1 = createJob("job1", Set(JobId("job1")))

    val result = DAG.order(List(job1))

    assert(result.isLeft)
    result.left.foreach { error =>
      assertEquals(error.message, "Cyclic dependency detected: job1 depends on job1")
    }
  }

  test("DAG.order detects duplicate job ids") {
    val job1 = createJob("duplicate")
    val job2 = createJob("duplicate")

    val result = DAG.order(List(job1, job2))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("Duplicate job ids"))
      assert(error.message.contains("duplicate"))
      assert(error.source.contains("test.ci"))
    }
  }

  test("DAG.order detects multiple duplicate job ids") {
    val job1 = createJob("dup1")
    val job2 = createJob("dup1")
    val job3 = createJob("dup2")
    val job4 = createJob("dup2")

    val result = DAG.order(List(job1, job2, job3, job4))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("Duplicate job ids"))
      assert(error.message.contains("dup1"))
      assert(error.message.contains("dup2"))
    }
  }

  test("DAG.order detects unknown dependencies") {
    val job1 = createJob("job1", Set(JobId("unknown")))

    val result = DAG.order(List(job1))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("Unknown dependencies"))
      assert(error.message.contains("unknown"))
    }
  }

  test("DAG.order detects multiple unknown dependencies") {
    val job1 = createJob("job1", Set(JobId("unknown1"), JobId("unknown2")))

    val result = DAG.order(List(job1))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.message.contains("Unknown dependencies"))
      assert(error.message.contains("unknown1"))
      assert(error.message.contains("unknown2"))
    }
  }

  test("DAG.order handles empty job list") {
    val result = DAG.order(List.empty[Job])

    assert(result.isRight)
    assertEquals(result.toOption.get, List.empty)
  }

  test("DAG.order handles single job with no dependencies") {
    val job = createJob("single")

    val result = DAG.order(List(job))

    assert(result.isRight)
    assertEquals(result.toOption.get, List(job))
  }

  test("DAG.order preserves job properties during ordering") {
    val resources = Resource(2000, 1024)
    val container = Some(Container("node:18"))
    val labels    = Set("production")
    val job1 = Job.one(
      JobId("job1"),
      dummyStep,
      resources = resources,
      container = container,
      labels = labels
    )
    val job2 = createJob("job2", Set(JobId("job1")))

    val result = DAG.order(List(job2, job1))

    assert(result.isRight)
    val ordered     = result.toOption.get
    val orderedJob1 = ordered.find(_.id == JobId("job1")).get
    assertEquals(orderedJob1.resources, resources)
    assertEquals(orderedJob1.container, container)
    assertEquals(orderedJob1.labels, labels)
  }

  test("DAG.order handles complex dependency graph") {
    val job1 = createJob("init")
    val job2 = createJob("build", Set(JobId("init")))
    val job3 = createJob("test-unit", Set(JobId("build")))
    val job4 = createJob("test-integration", Set(JobId("build")))
    val job5 = createJob("coverage", Set(JobId("test-unit"), JobId("test-integration")))
    val job6 = createJob("lint", Set(JobId("init")))
    val job7 = createJob("deploy", Set(JobId("coverage"), JobId("lint")))

    val result = DAG.order(List(job7, job5, job3, job6, job2, job4, job1))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.length, 7)

    val indices = ordered.map(_.id).zipWithIndex.toMap
    assert(indices(JobId("init")) < indices(JobId("build")))
    assert(indices(JobId("init")) < indices(JobId("lint")))
    assert(indices(JobId("build")) < indices(JobId("test-unit")))
    assert(indices(JobId("build")) < indices(JobId("test-integration")))
    assert(indices(JobId("test-unit")) < indices(JobId("coverage")))
    assert(indices(JobId("test-integration")) < indices(JobId("coverage")))
    assert(indices(JobId("coverage")) < indices(JobId("deploy")))
    assert(indices(JobId("lint")) < indices(JobId("deploy")))
  }

  test("DAG.order with job having multiple direct dependencies") {
    val job1 = createJob("dep1")
    val job2 = createJob("dep2")
    val job3 = createJob("dep3")
    val job4 = createJob("main", Set(JobId("dep1"), JobId("dep2"), JobId("dep3")))

    val result = DAG.order(List(job4, job2, job1, job3))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.last.id, JobId("main"))
    assert(ordered.take(3).map(_.id).toSet == Set(JobId("dep1"), JobId("dep2"), JobId("dep3")))
  }

  test("DAG.order handles mixture of jobs with and without dependencies") {
    val jobA = createJob("a")
    val jobB = createJob("b")
    val jobC = createJob("c", Set(JobId("a")))
    val jobD = createJob("d")
    val jobE = createJob("e", Set(JobId("c"), JobId("d")))

    val result = DAG.order(List(jobE, jobC, jobB, jobD, jobA))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.length, 5)

    val indices = ordered.map(_.id).zipWithIndex.toMap
    assert(indices(JobId("a")) < indices(JobId("c")))
    assert(indices(JobId("c")) < indices(JobId("e")))
    assert(indices(JobId("d")) < indices(JobId("e")))
  }

  test("DAG.order error messages are sorted alphabetically") {
    val job1 = createJob("zebra")
    val job2 = createJob("zebra")
    val job3 = createJob("alpha")
    val job4 = createJob("alpha")

    val result = DAG.order(List(job1, job2, job3, job4))

    assert(result.isLeft)
    result.left.foreach { error =>
      val message = error.message
      assert(message.indexOf("alpha") < message.indexOf("zebra"))
    }
  }

  test("DAG.order with unknown dependencies lists them sorted") {
    val job1 = createJob("job1", Set(JobId("zebra"), JobId("alpha"), JobId("beta")))

    val result = DAG.order(List(job1))

    assert(result.isLeft)
    result.left.foreach { error =>
      val message = error.message
      assert(message.indexOf("alpha") < message.indexOf("beta"))
      assert(message.indexOf("beta") < message.indexOf("zebra"))
    }
  }

  test("DAG.order returns ValidationError type for duplicate jobs") {
    val job1 = createJob("dup")
    val job2 = createJob("dup")

    val result = DAG.order(List(job1, job2))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.isInstanceOf[ValidationError])
      assert(error.source.isDefined)
    }
  }

  test("DAG.order returns ValidationError type for unknown dependencies") {
    val job1 = createJob("job1", Set(JobId("unknown")))

    val result = DAG.order(List(job1))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.isInstanceOf[ValidationError])
    }
  }

  test("DAG.order returns ValidationError type for cyclic dependencies") {
    val job1 = createJob("job1", Set(JobId("job2")))
    val job2 = createJob("job2", Set(JobId("job1")))

    val result = DAG.order(List(job1, job2))

    assert(result.isLeft)
    result.left.foreach { error =>
      assert(error.isInstanceOf[ValidationError])
    }
  }

  test("DAG.order with transitive dependencies") {
    val job1 = createJob("a")
    val job2 = createJob("b", Set(JobId("a")))
    val job3 = createJob("c", Set(JobId("b")))
    val job4 = createJob("d", Set(JobId("c")))
    val job5 = createJob("e", Set(JobId("d")))

    val result = DAG.order(List(job5, job4, job3, job2, job1))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.map(_.id), List(JobId("a"), JobId("b"), JobId("c"), JobId("d"), JobId("e")))
  }

  test("DAG.order with wide dependency tree") {
    val root     = createJob("root")
    val children = (1 to 10).map(i => createJob(s"child-$i", Set(JobId("root")))).toList

    val result = DAG.order(children ++ List(root))

    assert(result.isRight)
    val ordered = result.toOption.get
    assertEquals(ordered.head.id, JobId("root"))
    assertEquals(ordered.tail.map(_.id).toSet, children.map(_.id).toSet)
  }

  test("DAG.order maintains stable ordering for independent jobs") {
    val job1 = createJob("job1")
    val job2 = createJob("job2")
    val job3 = createJob("job3")

    val result1 = DAG.order(List(job1, job2, job3))
    val result2 = DAG.order(List(job1, job2, job3))

    assert(result1.isRight && result2.isRight)
    assertEquals(result1.toOption.get.map(_.id), result2.toOption.get.map(_.id))
  }
