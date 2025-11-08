ThisBuild / version      := "1.0.0-SNAPSHOT"
ThisBuild / organization := "com.verschraenkt.ci"
ThisBuild / scalaVersion := "3.3.7"

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-source:3.3",
    "-rewrite"
  ),
  resolvers += "Akka library repository".at("https://repo.akka.io/maven")
)

lazy val V = new {
  val pekko          = "1.2.1"
  val pekkoHttp      = "1.3.0"
  val catsEffect     = "3.6.3"
  val caliban        = "2.11.1"
  val circe          = "0.14.15"
  val http4s         = "0.23.30"
  val sttp           = "4.0.13"
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
  val cyclonedx      = "11.0.0"
  val logback        = "1.5.20"
  val testcontainers = "0.43.6"
  val scalaTest      = "3.2.19"
  val munit          = "2.1.0"
}

lazy val pekkoDeps = Seq(
  "org.apache.pekko" %% "pekko-actor-typed"            % V.pekko,
  "org.apache.pekko" %% "pekko-cluster-typed"          % V.pekko,
  "org.apache.pekko" %% "pekko-cluster-sharding-typed" % V.pekko,
  "org.apache.pekko" %% "pekko-stream-typed"           % V.pekko,
  "org.apache.pekko" %% "pekko-http"                   % V.pekkoHttp,
  "org.apache.pekko" %% "pekko-persistence-typed"      % V.pekko,
  "org.apache.pekko" %% "pekko-persistence-jdbc"       % "1.1.1",
  "org.apache.pekko" %% "pekko-serialization-jackson"  % V.pekko
)

lazy val databaseDeps = Seq(
  "com.typesafe.slick" %% "slick"          % "3.5.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.5.1",
  "org.postgresql"      % "postgresql"     % "42.7.8",
  "io.getquill"        %% "quill-jdbc-zio" % "4.8.6"
)

lazy val cacheDeps = Seq(
  "dev.profunktor" %% "redis4cats-effects" % V.redis4cats,
  "dev.profunktor" %% "redis4cats-streams" % V.redis4cats,
  "io.lettuce"      % "lettuce-core"       % "7.0.0.RELEASE"
)

lazy val storageDeps = Seq(
  "software.amazon.awssdk" % "s3"    % V.awsSdk,
  "io.minio"               % "minio" % V.minio
)

lazy val containerDeps = Seq(
  "com.github.docker-java" % "docker-java-core"                  % V.dockerJava,
  "com.github.docker-java" % "docker-java-transport-httpclient5" % V.dockerJava,
  "io.fabric8"             % "kubernetes-client"                 % V.kubernetes
)

lazy val observabilityDeps = Seq(
  "io.opentelemetry" % "opentelemetry-api"                      % V.opentelemetry,
  "io.opentelemetry" % "opentelemetry-sdk"                      % V.opentelemetry,
  "io.opentelemetry" % "opentelemetry-exporter-otlp"            % V.opentelemetry,
  "io.prometheus"    % "prometheus-metrics-core"                % V.prometheus,
  "io.prometheus"    % "prometheus-metrics-exporter-httpserver" % V.prometheus
)

lazy val configDeps = Seq(
  "org.virtuslab" %% "scala-yaml" % "0.3.0",
  "is.cir"        %% "ciris"      % "3.6.0",
  "com.typesafe"   % "config"     % "1.4.3"
)

lazy val jsonDeps = Seq(
  "io.circe" %% "circe-core"    % V.circe,
  "io.circe" %% "circe-generic" % V.circe,
  "io.circe" %% "circe-parser"  % V.circe,
  "io.circe" %% "circe-yaml"    % "1.15.0"
)

lazy val httpDeps = Seq(
  "com.softwaremill.sttp.client4" %% "core"  % V.sttp,
  "com.softwaremill.sttp.client4" %% "cats"  % V.sttp,
  "com.softwaremill.sttp.client4" %% "circe" % V.sttp
)

lazy val grpcDeps = Seq(
  "com.thesamet.scalapb" %% "scalapb-runtime"      % V.scalapb,
  "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % V.scalapb,
  "io.grpc"               % "grpc-netty"           % V.grpc,
  "io.grpc"               % "grpc-services"        % V.grpc
)

lazy val securityDeps = Seq(
  "com.github.jwt-scala" %% "jwt-circe"           % V.jwt,
  "org.cyclonedx"         % "cyclonedx-core-java" % V.cyclonedx,
  "com.nimbusds"          % "nimbus-jose-jwt"     % "10.6"
)

lazy val wasmDeps = Seq(
  "io.github.kawamuray.wasmtime" % "wasmtime-java" % "0.19.0"
)

lazy val loggingDeps = Seq(
  "ch.qos.logback"              % "logback-classic"          % V.logback,
  "net.logstash.logback"        % "logstash-logback-encoder" % "9.0",
  "com.typesafe.scala-logging" %% "scala-logging"            % "3.9.6"
)

lazy val catsEffectDeps = Seq(
  "org.typelevel" %% "cats-effect"        % V.catsEffect,
  "org.typelevel" %% "cats-effect-kernel" % V.catsEffect,
  "org.typelevel" %% "cats-effect-std"    % V.catsEffect
)

lazy val calibanDeps = Seq(
  "com.github.ghostdogpr" %% "caliban"            % V.caliban,
  "com.github.ghostdogpr" %% "caliban-pekko-http" % V.caliban,
  "com.github.ghostdogpr" %% "caliban-cats"       % V.caliban
)

lazy val testDeps = Seq(
  "org.apache.pekko" %% "pekko-actor-testkit-typed"       % V.pekko          % Test,
  "org.apache.pekko" %% "pekko-stream-testkit"            % V.pekko          % Test,
  "org.typelevel"    %% "cats-effect-testkit"             % V.catsEffect     % Test,
  "org.typelevel"    %% "munit-cats-effect"               % V.munit          % Test,
  "org.scalameta"    %% "munit"                           % "1.2.1"          % Test,
  "org.scalatest"    %% "scalatest"                       % V.scalaTest      % Test,
  "com.dimafeng"     %% "testcontainers-scala-postgresql" % V.testcontainers % Test,
  "com.dimafeng"     %% "testcontainers-scala-minio"      % V.testcontainers % Test,
  "com.dimafeng"     %% "testcontainers-scala-core"       % V.testcontainers % Test
)

lazy val core = (project in file("modules/core"))
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-core",
    libraryDependencies ++=
      jsonDeps ++
        configDeps ++
        testDeps
  )

lazy val dsl = (project in file("modules/dsl"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-dsl",
    libraryDependencies ++=
      jsonDeps ++
        testDeps
  )

lazy val storage = (project in file("modules/storage"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-storage",
    libraryDependencies ++=
      databaseDeps ++
        cacheDeps ++
        storageDeps ++
        testDeps
  )

lazy val executor = (project in file("modules/executor"))
  .dependsOn(core % "compile->compile;test->test", storage)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-executor",
    libraryDependencies ++=
      containerDeps ++
        wasmDeps ++
        testDeps
  )

lazy val engine = (project in file("modules/engine"))
  .dependsOn(core % "compile->compile;test->test", executor, storage)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-engine",
    libraryDependencies ++=
      pekkoDeps ++
        loggingDeps ++
        testDeps
  )

lazy val plugin = (project in file("modules/plugin"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-plugin",
    libraryDependencies ++=
      grpcDeps ++
        wasmDeps ++
        testDeps,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    )
  )

lazy val security = (project in file("modules/security"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-security",
    libraryDependencies ++=
      securityDeps ++
        httpDeps ++
        testDeps
  )

lazy val observability = (project in file("modules/observability"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-observability",
    libraryDependencies ++=
      observabilityDeps ++
        testDeps
  )

lazy val api = (project in file("modules/api"))
  .dependsOn(
    core % "compile->compile;test->test",
    engine,
    storage,
    security,
    observability,
    dsl
  )
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-api",
    libraryDependencies ++=
      calibanDeps ++
        httpDeps ++
        testDeps
  )

lazy val server = (project in file("modules/server"))
  .dependsOn(
    engine,
    api,
    plugin,
    security,
    observability
  )
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-server",
    libraryDependencies ++= testDeps,
    assembly / mainClass := Some("org.verschraenkt.ci.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
      case PathList("META-INF", xs @ _*) =>
        xs.map(_.toLowerCase) match {
          case "manifest.mf" :: Nil | "index.list" :: Nil | "dependencies" :: Nil =>
            MergeStrategy.discard
          case ps @ (x :: _) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
            MergeStrategy.discard
          case "services" :: _ =>
            MergeStrategy.concat
          case _ => MergeStrategy.first
        }
      case "application.conf" => MergeStrategy.concat
      case "reference.conf"   => MergeStrategy.concat
      case _                  => MergeStrategy.first
    }
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)

lazy val cli = (project in file("modules/cli"))
  .dependsOn(core % "compile->compile;test->test", api)
  .settings(commonSettings)
  .settings(
    name := "verschraenkt-ci-cli",
    libraryDependencies ++=
      httpDeps ++
        Seq("com.github.scopt" %% "scopt" % "4.1.0") ++
        testDeps,
    assembly / mainClass := Some("org.verschraenkt.ci.cli.Main")
  )

lazy val root = (project in file("."))
  .aggregate(
    core,
    dsl,
    storage,
    executor,
    engine,
    plugin,
    security,
    observability,
    api,
    server,
    cli
  )
  .settings(commonSettings)
  .settings(
    name           := "verschraenkt-ci",
    publish / skip := true
  )
