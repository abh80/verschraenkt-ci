ThisBuild / version      := "1.0.0-SNAPSHOT"
ThisBuild / organization := "com.verschraenkt.ci"
ThisBuild / scalaVersion := "3.8.1"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Werror",
    "-source:3.8",
    "-Wall",
    "-Wunused:imports"
  ),
  resolvers += "Akka library repository".at("https://repo.akka.io/maven"),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision
)

/* =========================
 * Versions
 * ========================= */

lazy val V = new {
  val pekko          = "1.2.1"
  val pekkoHttp      = "1.3.0"
  val catsEffect     = "3.6.3"
  val caliban        = "2.11.1"
  val circe          = "0.14.15"
  val redis4cats     = "2.0.1"
  val awsSdk         = "2.38.1"
  val minio          = "8.6.0"
  val dockerJava     = "3.6.0"
  val kubernetes     = "7.4.0"
  val opentelemetry  = "1.55.0"
  val prometheus     = "1.4.2"
  val grpc           = "1.68.1"
  val scalapb        = "0.11.20"
  val jwt            = "11.0.3"
  val logback        = "1.5.20"
  val testcontainers = "0.43.6"
  val scalaTest      = "3.2.19"
  val munit          = "2.1.0"
  val mockito        = "5.11.0"
}

/* =========================
 * Dependency Groups
 * ========================= */

lazy val pekkoDeps = Seq(
  "org.apache.pekko" %% "pekko-actor-typed"       % V.pekko,
  "org.apache.pekko" %% "pekko-cluster-typed"     % V.pekko,
  "org.apache.pekko" %% "pekko-stream-typed"      % V.pekko,
  "org.apache.pekko" %% "pekko-persistence-typed" % V.pekko,
  "org.apache.pekko" %% "pekko-http"              % V.pekkoHttp
)

lazy val jsonDeps = Seq(
  "io.circe"      %% "circe-core"    % V.circe,
  "io.circe"      %% "circe-generic" % V.circe,
  "io.circe"      %% "circe-parser"  % V.circe,
  "org.typelevel" %% "cats-core"     % "2.13.0"
)

lazy val configDeps = Seq(
  "is.cir"      %% "ciris"  % "3.6.0",
  "com.typesafe" % "config" % "1.4.3"
)

lazy val storageDeps = Seq(
  "com.typesafe.slick"    %% "slick"          % "3.5.1",
  "com.typesafe.slick"    %% "slick-hikaricp" % "3.5.1",
  "org.postgresql"         % "postgresql"     % "42.7.8",
  "com.github.tminglei"    % "slick-pg_3"     % "0.22.2",
  "software.amazon.awssdk" % "s3"             % V.awsSdk,
  "io.minio"               % "minio"          % V.minio,
  "org.typelevel"         %% "cats-effect"    % V.catsEffect
)

lazy val executorDeps = Seq(
  "com.github.docker-java" % "docker-java-core"                  % V.dockerJava,
  "com.github.docker-java" % "docker-java-transport-httpclient5" % V.dockerJava,
  "io.fabric8"             % "kubernetes-client"                 % V.kubernetes
)

lazy val observabilityDeps = Seq(
  "io.opentelemetry" % "opentelemetry-api"       % V.opentelemetry,
  "io.opentelemetry" % "opentelemetry-sdk"       % V.opentelemetry,
  "io.prometheus"    % "prometheus-metrics-core" % V.prometheus
)

lazy val testDeps = Seq(
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % V.pekko     % Test,
  "org.scalatest"    %% "scalatest"                 % V.scalaTest % Test,
  "org.typelevel"    %% "munit-cats-effect"         % V.munit     % Test,
  "org.mockito"       % "mockito-core"              % V.mockito   % Test
)

lazy val integrationTestDeps = Seq(
  "com.dimafeng" %% "testcontainers-scala-postgresql" % V.testcontainers % "it,test",
  "com.dimafeng" %% "testcontainers-scala-munit"      % V.testcontainers % "it,test"
)

/* =========================
 * Core Domain
 * ========================= */

lazy val core = (project in file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-core",
    libraryDependencies ++= jsonDeps ++ configDeps ++ testDeps
  )

/* =========================
 * DSL
 * ========================= */

lazy val dsl = (project in file("modules/dsl"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-dsl",
    libraryDependencies ++= testDeps
  )

/* =========================
 * Engine API
 * ========================= */

lazy val engineApi = (project in file("modules/engine-api"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-engine-api"
  )

/* =========================
 * Storage
 * ========================= */

lazy val storage = (project in file("modules/storage"))
  .dependsOn(core)
  .configs(IntegrationTest)
  .settings(commonSettings)
  .settings(Defaults.itSettings)
  .settings(
    name := "verschraenkt-ci-storage",
    libraryDependencies ++= storageDeps ++ testDeps
  )

/* =========================
 * Engine (Control Plane)
 * ========================= */

lazy val engine = (project in file("modules/engine"))
  .dependsOn(core, engineApi, storage)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-engine",
    libraryDependencies ++= pekkoDeps ++ testDeps
  )

/* =========================
 * Executor (Data Plane)
 * ========================= */

lazy val executor = (project in file("modules/executor"))
  .dependsOn(core, engineApi)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-executor",
    libraryDependencies ++= executorDeps ++ testDeps
  )

/* =========================
 * Observability
 * ========================= */

lazy val observability = (project in file("modules/observability"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-observability",
    libraryDependencies ++= observabilityDeps
  )

/* =========================
 * Security
 * ========================= */

lazy val security = (project in file("modules/security"))
  .dependsOn(core)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-security"
  )

/* =========================
 * API
 * ========================= */

lazy val api = (project in file("modules/api"))
  .dependsOn(core, dsl, engine, engineApi, storage, security, observability)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-api"
  )

/* =========================
 * Server (ONLY WIRING POINT)
 * ========================= */

lazy val server = (project in file("modules/server"))
  .dependsOn(engine, executor, api, observability, security)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-server"
  )

/* =========================
 * CLI
 * ========================= */

lazy val cli = (project in file("modules/cli"))
  .dependsOn(api)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-cli"
  )

/* =========================
 * Root
 * ========================= */

lazy val root = (project in file("."))
  .aggregate(
    core,
    dsl,
    engineApi,
    storage,
    engine,
    executor,
    observability,
    security,
    api,
    server,
    cli
  )
  .settings(commonSettings)
  .settings(
    name           := "verschraenkt-ci",
    publish / skip := true
  )
