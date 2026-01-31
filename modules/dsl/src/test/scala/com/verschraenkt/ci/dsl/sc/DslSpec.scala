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

import cats.data.NonEmptyVector
import cats.syntax.foldable.toUnorderedFoldableOps
import com.verschraenkt.ci.core.model.*
import munit.FunSuite

import scala.concurrent.duration.*

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

  // Tests for Issue 2.2: cache functions require at least one path
  test("restoreCache throws IllegalArgumentException when no paths provided") {
    intercept[IllegalArgumentException] {
      pipeline("test") {
        workflow("w") {
          job("j") {
            steps {
              restoreCache("key")
            }
          }
        }
      }
    }
  }

  test("saveCache throws IllegalArgumentException when no paths provided") {
    intercept[IllegalArgumentException] {
      pipeline("test") {
        workflow("w") {
          job("j") {
            steps {
              saveCache("key")
            }
          }
        }
      }
    }
  }

  test("restoreBranchCache throws IllegalArgumentException when no paths provided") {
    intercept[IllegalArgumentException] {
      pipeline("test") {
        workflow("w") {
          job("j") {
            steps {
              restoreBranchCache("key", "main")
            }
          }
        }
      }
    }
  }

  test("restoreS3Cache throws IllegalArgumentException when no paths provided") {
    intercept[IllegalArgumentException] {
      pipeline("test") {
        workflow("w") {
          job("j") {
            steps {
              restoreS3Cache("bucket", "region", "key")
            }
          }
        }
      }
    }
  }

  test("cache functions create correct Step types and verify properties") {
    val p: Pipeline =
      pipeline("cache-verification") {
        workflow("test") {
          job("verify-cache-functions") {
            steps {
              checkout()

              // Test basic cache
              restoreCache("basic", "path1", "path2")
              saveCache("basic", "path1", "path2")

              // Test scoped caches
              restoreBranchCache("branch", "main", "branch-path")
              saveBranchCache("branch", "main", "branch-path")
              restorePRCache("pr", "123", "pr-path")
              savePRCache("pr", "123", "pr-path")
              restoreTagCache("tag", "v1.0", "tag-path")
              saveTagCache("tag", "v1.0", "tag-path")

              // Test external caches
              restoreS3Cache("s3", "us-east-1", "s3-key", "s3-path")
              saveS3Cache("s3", "us-east-1", "s3-key", "s3-path")
              restoreGCSCache("gcs", "gcs-bucket", "gcs-key", "gcs-path")
              saveGCSCache("gcs", "gcs-bucket", "gcs-key", "gcs-path")
            }
          }
        }
      }

    val j = p.workflows.head.jobs.head
    val s = j.steps.toVector

    // Should have: 1 checkout + 10 cache operations = 11 total steps
    assertEquals(s.size, 13)

    // Verify basic cache restore
    val basicRestore = s(1).asInstanceOf[Step.RestoreCache]
    assertEquals(basicRestore.cache.key.value, "basic")
    assertEquals(basicRestore.cache.scope, CacheScope.Global)
    assertEquals(basicRestore.paths.toList, List("path1", "path2"))

    // Verify basic cache save
    val basicSave = s(2).asInstanceOf[Step.SaveCache]
    assertEquals(basicSave.cache.key.value, "basic")
    assertEquals(basicSave.cache.scope, CacheScope.Global)
    assertEquals(basicSave.paths.toList, List("path1", "path2"))

    // Verify branch cache restore
    val branchRestore = s(3).asInstanceOf[Step.RestoreCache]
    assertEquals(branchRestore.cache.key.value, "main:branch")
    assertEquals(branchRestore.cache.scope, CacheScope.Branch)
    assertEquals(branchRestore.paths.toList, List("branch-path"))

    // Verify branch cache save
    val branchSave = s(4).asInstanceOf[Step.SaveCache]
    assertEquals(branchSave.cache.key.value, "main:branch")
    assertEquals(branchSave.cache.scope, CacheScope.Branch)
    assertEquals(branchSave.paths.toList, List("branch-path"))

    // Verify PR cache restore
    val prRestore = s(5).asInstanceOf[Step.RestoreCache]
    assertEquals(prRestore.cache.key.value, "123:pr")
    assertEquals(prRestore.cache.scope, CacheScope.PullRequest)
    assertEquals(prRestore.paths.toList, List("pr-path"))

    // Verify PR cache save
    val prSave = s(6).asInstanceOf[Step.SaveCache]
    assertEquals(prSave.cache.key.value, "123:pr")
    assertEquals(prSave.cache.scope, CacheScope.PullRequest)
    assertEquals(prSave.paths.toList, List("pr-path"))

    // Verify tag cache restore
    val tagRestore = s(7).asInstanceOf[Step.RestoreCache]
    assertEquals(tagRestore.cache.key.value, "v1.0:tag")
    assertEquals(tagRestore.cache.scope, CacheScope.Tag)
    assertEquals(tagRestore.paths.toList, List("tag-path"))

    // Verify tag cache save
    val tagSave = s(8).asInstanceOf[Step.SaveCache]
    assertEquals(tagSave.cache.key.value, "v1.0:tag")
    assertEquals(tagSave.cache.scope, CacheScope.Tag)
    assertEquals(tagSave.paths.toList, List("tag-path"))

    // Verify S3 cache restore
    val s3Restore      = s(9).asInstanceOf[Step.RestoreCache]
    val s3RestoreCache = s3Restore.cache.asInstanceOf[S3Cache]
    assertEquals(s3RestoreCache.bucket, "s3")
    assertEquals(s3RestoreCache.region, "us-east-1")
    assertEquals(s3RestoreCache.key.value, "s3-key")
    assertEquals(s3RestoreCache.scope, CacheScope.Global)
    assertEquals(s3Restore.paths.toList, List("s3-path"))

    // Verify S3 cache save
    val s3Save      = s(10).asInstanceOf[Step.SaveCache]
    val s3SaveCache = s3Save.cache.asInstanceOf[S3Cache]
    assertEquals(s3SaveCache.bucket, "s3")
    assertEquals(s3SaveCache.region, "us-east-1")
    assertEquals(s3SaveCache.key.value, "s3-key")
    assertEquals(s3SaveCache.scope, CacheScope.Global)
    assertEquals(s3Save.paths.toList, List("s3-path"))
  }

  test("comprehensive dsl test") {
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
              restoreCache("npm-deps", "node_modules", "package-lock.json")
              run("npm ci")
              saveCache("npm-deps", "node_modules", "package-lock.json")
              run("sbt scalafmtCheckAll")
              run("sbt scalafixAll")
              run("npm run lint", shell = ShellKind.Bash)
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
              restoreBranchCache("sbt-deps", "feature-branch", "target/dependency-cache", ".gradle")
              run("sbt ++$scala update")
              saveBranchCache("sbt-deps", "feature-branch", "target/dependency-cache", ".gradle")
              restoreCache("test-data", "target/test-classes")
              run("scripts/setup-db.sh $db")
              saveCache("test-data", "target/test-classes")
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
              restoreS3Cache("frontend-assets", "us-east-1", "npm-frontend-$node", "node_modules")
              run("nvm use $node && npm ci")
              saveS3Cache("frontend-assets", "us-east-1", "npm-frontend-$node", "node_modules")
              restorePRCache("build-cache", "123", "dist", ".next")
              run("nvm use $node && npm test")
              run("nvm use $node && npm run build")
              savePRCache("build-cache", "123", "dist", ".next")
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
              restoreGCSCache("e2e-deps", "e2e-test-bucket", "test-data", "fixtures")
              run("docker compose pull")
              run("docker compose up -d")
              run("npm run e2e")
              run("docker compose logs")
              run("docker compose down --volumes")
              saveGCSCache("e2e-deps", "e2e-test-bucket", "test-results", "reports")
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
              restoreTagCache("deploy-config", "v1.2.3", "k8s-manifests", "helm-values")
              run("scripts/render-config.sh staging")
              run("scripts/apply-k8s.sh staging")
              saveTagCache("deploy-status", "staging-deploy-123", "deploy-logs", "status-files")
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
    assertEquals(lintJob.steps.size, 7L)
  }
