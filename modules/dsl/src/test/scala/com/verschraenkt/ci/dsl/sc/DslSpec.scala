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

import cats.syntax.foldable.toUnorderedFoldableOps
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*
import cats.data.NonEmptyVector

class DslSpec extends FunSuite:
  import dsl.*

  test("simple dsl test") {
    val p = pipeline("p") {
      workflow("w") {
        job("j") {
          steps {
            run("s")
          }
        }
      }
    }
    assertEquals(p.id, PipelineId("p"))
  }

  test("dsl test") {
    val pipelineDef: Pipeline =
      pipeline("monorepo-ci") {
        // Pipeline-level configurations
        timeout(2.hours)
        labels("service:monorepo", "owner:platform", "env:multi")
        concurrency("monorepo-ci")

        // Build and Test Workflow
        workflow("build-and-test") {
          defaultContainer(
            image = "ghcr.io/example/ci-base:latest",
            args = List("--log-level", "info"),
            env = Map(
              "CI"       -> "true",
              "SBT_OPTS" -> "-Xmx2G -Xms512M"
            ),
            workdir = Some("/workspace")
          )

          // Lint and Static Checks Job
          job("lint-and-static-checks") {
            jobLabels("lint", "static-analysis")
            jobTimeout(20.minutes)
            resources(cpuMilli = 1000, memoryMiB = 1024, diskMiB = 5.GB.toInt)
            steps {
              checkout()
              run("sbt scalafmtCheckAll")
              run("sbt scalafixAll")
              run("npm ci && npm run lint", shell = ShellKind.Bash)
            }
          }

          // Backend Unit Tests Job
          job("backend-unit-tests") {
            needs(JobId("lint-and-static-checks"))
            jobLabels("backend", "unit-tests")
            jobTimeout(45.minutes)
            resources(cpuMilli = 2500, memoryMiB = 4096, diskMiB = 20.GB.toInt)
            matrix(
              Map(
                "scala" -> NonEmptyVector.of("2.13.14", "3.3.1"),
                "db"    -> NonEmptyVector.of("postgres", "mysql")
              )
            )
            steps {
              checkout()
              run("scripts/setup-db.sh $db")
              run("sbt ++$scala clean test")
            }
          }

          // Frontend Unit Tests Job
          job("frontend-unit-tests") {
            needs(JobId("lint-and-static-checks"))
            jobLabels("frontend", "unit-tests")
            resources(cpuMilli = 2000, memoryMiB = 3072, diskMiB = 10.GB.toInt)
            matrix(
              Map(
                "node" -> NonEmptyVector.of("18", "20")
              )
            )
            steps {
              checkout()
              run("nvm use $node && npm ci")
              run("nvm use $node && npm test")
            }
          }

          // End-to-End Tests Job
          job("e2e-tests") {
            needs(JobId("backend-unit-tests"), JobId("frontend-unit-tests"))
            jobLabels("e2e", "integration")
            jobTimeout(1.hour)
            resources(cpuMilli = 4000, memoryMiB = 8192, diskMiB = 40.GB.toInt)
            steps {
              checkout()
              run("docker compose pull")
              run("docker compose up -d")
              run("npm run e2e")
              run("docker compose logs")
              run("docker compose down --volumes")
            }
          }
        }

        // Deploy Workflow
        workflow("deploy") {
          workflowCondition(Condition.OnBranch("main", PatternKind.Exact))
          defaultContainer(
            image = "ghcr.io/example/deploy-base:latest",
            env = Map(
              "CI"         -> "true",
              "KUBECONFIG" -> "/kube/config"
            ),
            workdir = Some("/workspace")
          )

          // Deploy to Staging Job
          job("deploy-staging") {
            needs(JobId("e2e-tests"))
            jobLabels("deploy", "staging")
            jobConcurrency("deploy-staging")
            jobTimeout(30.minutes)
            resources(cpuMilli = 1500, memoryMiB = 2048, diskMiB = 5.GB.toInt)
            steps {
              checkout()
              run("scripts/render-config.sh staging")
              run("scripts/apply-k8s.sh staging")
              run("kubectl rollout status deploy/monorepo-api -n staging")
            }
          }

          // Smoke Tests on Staging Job
          job("smoke-staging") {
            needs(JobId("deploy-staging"))
            jobLabels("deploy", "staging", "smoke")
            resources(cpuMilli = 1000, memoryMiB = 1024, diskMiB = 2.GB.toInt)
            steps {
              run("scripts/smoke.sh staging")
            }
          }

          // Deploy to Production Job
          job("deploy-production") {
            needs(JobId("smoke-staging"))
            jobLabels("deploy", "production")
            jobCondition(Condition.OnEvent("workflow_dispatch"))
            jobConcurrency("deploy-production")
            jobTimeout(45.minutes)
            resources(cpuMilli = 2000, memoryMiB = 3072, diskMiB = 5.GB.toInt)
            steps {
              checkout()
              run("scripts/render-config.sh production")
              run("scripts/apply-k8s.sh production")
              run("kubectl rollout status deploy/monorepo-api -n production")
            }
          }

          // Smoke Tests on Production Job
          job("smoke-production") {
            needs(JobId("deploy-production"))
            jobLabels("deploy", "production", "smoke")
            resources(cpuMilli = 1000, memoryMiB = 1024, diskMiB = 2.GB.toInt)
            steps {
              run("scripts/smoke.sh production")
            }
          }
        }

        // Nightly Maintenance Workflow
        workflow("nightly-maintenance") {
          workflowCondition(Condition.OnSchedule("0 0 * * *"))
          defaultContainer(
            image = "ghcr.io/example/maintenance-base:latest",
            env = Map("CI" -> "true")
          )

          // Dry Run Database Migrations Job
          job("db-migrations-dry-run") {
            jobLabels("maintenance", "db", "dry-run")
            resources(cpuMilli = 1000, memoryMiB = 1024, diskMiB = 2.GB.toInt)
            steps {
              checkout()
              run("scripts/migrate.sh --dry-run")
            }
          }

          // Apply Database Migrations Job
          job("db-migrations-apply") {
            needs(JobId("db-migrations-dry-run"))
            jobLabels("maintenance", "db", "migrate")
            resources(cpuMilli = 1000, memoryMiB = 1024, diskMiB = 2.GB.toInt)
            steps {
              checkout()
              run("scripts/migrate.sh --apply")
            }
          }

          // Cleanup Old Artifacts Job
          job("cleanup-old-artifacts") {
            jobLabels("maintenance", "cleanup")
            resources(cpuMilli = 500, memoryMiB = 512, diskMiB = 1.GB.toInt)
            steps {
              run("scripts/cleanup-artifacts.sh 30")
            }
          }
        }
      }

    // Verify pipeline ID
    assertEquals(pipelineDef.id, PipelineId("monorepo-ci"))

    // Verify pipeline-level configurations
    assertEquals(pipelineDef.timeout, Some(2.hours))
    assertEquals(pipelineDef.labels, Set("service:monorepo", "owner:platform", "env:multi"))
    assertEquals(pipelineDef.concurrencyGroup, Some("monorepo-ci"))

    // Verify workflows
    val workflows = pipelineDef.workflows
    assertEquals(workflows.size.toInt, 3)

    // Verify "build-and-test" workflow
    val buildAndTestWorkflow = workflows.find(_.name == "build-and-test").get
    assertEquals(buildAndTestWorkflow.defaultContainer.map(_.image), Some("ghcr.io/example/ci-base:latest"))
    assertEquals(buildAndTestWorkflow.jobs.size.toInt, 4)

    // Verify "lint-and-static-checks" job
    val lintJob = buildAndTestWorkflow.jobs.find(_.id == JobId("lint-and-static-checks")).get
    assertEquals(lintJob.labels, Set("lint", "static-analysis"))
    assertEquals(lintJob.timeout, 20.minutes)
    assertEquals(lintJob.resources.cpuMilli, 1000)
    assertEquals(lintJob.resources.memoryMiB, 1024)
    assertEquals(lintJob.resources.diskMiB, 5.GB.toInt)
    assertEquals(lintJob.steps.size, 4L)
    assertEquals(
      StepUtils.extractCommandStrings(lintJob.steps.toVector),
      Seq(
        None,
        Some("sbt scalafmtCheckAll"),
        Some("sbt scalafixAll"),
        Some("npm ci && npm run lint")
      )
    )

    // Verify "backend-unit-tests" job
    val backendJob = buildAndTestWorkflow.jobs.find(_.id == JobId("backend-unit-tests")).get
    assertEquals(backendJob.labels, Set("backend", "unit-tests"))
    assertEquals(backendJob.timeout, 45.minutes)
    assertEquals(backendJob.resources.cpuMilli, 2500)
    assertEquals(backendJob.resources.memoryMiB, 4096)
    assertEquals(backendJob.resources.diskMiB, 20.GB.toInt)
    assertEquals(
      backendJob.matrix,
      Map(
        "scala" -> NonEmptyVector.of("2.13.14", "3.3.1"),
        "db"    -> NonEmptyVector.of("postgres", "mysql")
      )
    )
    assertEquals(
      StepUtils.extractCommandStrings(backendJob.steps.toVector),
      Seq(
        None,
        Some("scripts/setup-db.sh $db"),
        Some("sbt ++$scala clean test")
      )
    )

    // Verify "frontend-unit-tests" job
    val frontendJob = buildAndTestWorkflow.jobs.find(_.id == JobId("frontend-unit-tests")).get
    assertEquals(frontendJob.labels, Set("frontend", "unit-tests"))
    assertEquals(frontendJob.resources.cpuMilli, 2000)
    assertEquals(frontendJob.resources.memoryMiB, 3072)
    assertEquals(frontendJob.resources.diskMiB, 10.GB.toInt)
    assertEquals(
      frontendJob.matrix,
      Map(
        "node" -> NonEmptyVector.of("18", "20")
      )
    )
    assertEquals(
      StepUtils.extractCommandStrings(frontendJob.steps.toVector),
      Seq(
        None,
        Some("nvm use $node && npm ci"),
        Some("nvm use $node && npm test")
      )
    )

    // Verify "e2e-tests" job
    val e2eJob = buildAndTestWorkflow.jobs.find(_.id == JobId("e2e-tests")).get
    assertEquals(e2eJob.labels, Set("e2e", "integration"))
    assertEquals(e2eJob.timeout, 1.hour)
    assertEquals(e2eJob.resources.cpuMilli, 4000)
    assertEquals(e2eJob.resources.memoryMiB, 8192)
    assertEquals(e2eJob.resources.diskMiB, 40.GB.toInt)
    assertEquals(
      StepUtils.extractCommandStrings(e2eJob.steps.toVector),
      Seq(
        None,
        Some("docker compose pull"),
        Some("docker compose up -d"),
        Some("npm run e2e"),
        Some("docker compose logs"),
        Some("docker compose down --volumes")
      )
    )

    // Verify "deploy" workflow
    val deployWorkflow = workflows.find(_.name == "deploy").get
    assertEquals(deployWorkflow.defaultContainer.map(_.image), Some("ghcr.io/example/deploy-base:latest"))
    assertEquals(deployWorkflow.jobs.size, 4L)

    // Verify "deploy-staging" job
    val deployStagingJob = deployWorkflow.jobs.find(_.id == JobId("deploy-staging")).get
    assertEquals(deployStagingJob.labels, Set("deploy", "staging"))
    assertEquals(deployStagingJob.timeout, 30.minutes)
    assertEquals(deployStagingJob.resources.cpuMilli, 1500)
    assertEquals(deployStagingJob.resources.memoryMiB, 2048)
    assertEquals(deployStagingJob.resources.diskMiB, 5.GB.toInt)
    assertEquals(
      StepUtils.extractCommandStrings(deployStagingJob.steps.toVector),
      Seq(
        None,
        Some("scripts/render-config.sh staging"),
        Some("scripts/apply-k8s.sh staging"),
        Some("kubectl rollout status deploy/monorepo-api -n staging")
      )
    )
  }