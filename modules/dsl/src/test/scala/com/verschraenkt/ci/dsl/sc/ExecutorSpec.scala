package com.verschraenkt.ci.dsl.sc

import com.verschraenkt.ci.core.model.Container
import munit.FunSuite

class ExecutorSpec extends FunSuite:

  test("Docker.Exec should create a valid container") {
    val dockerExec = Docker.Exec(
      image = "ubuntu:latest",
      args = List("--version"),
      env = Map("VAR" -> "value"),
      user = Some("testuser"),
      workdir = Some("/test")
    )

    val container = dockerExec.toContainer

    assertEquals(container.image, "ubuntu:latest")
    assertEquals(container.args, List("--version"))
    assertEquals(container.env, Map("VAR" -> "value"))
    assertEquals(container.user, Some("testuser"))
    assertEquals(container.workdir, Some("/test"))
  }

  test("Docker.Exec should create a valid container with default values") {
    val dockerExec = Docker.Exec(image = "ubuntu:latest")

    val container = dockerExec.toContainer

    assertEquals(container.image, "ubuntu:latest")
    assertEquals(container.args, Nil)
    assertEquals(container.env, Map.empty)
    assertEquals(container.user, None)
    assertEquals(container.workdir, None)
  }
