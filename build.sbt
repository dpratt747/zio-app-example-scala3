ThisBuild / scalaVersion := "3.7.4"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.dpratt747"
ThisBuild / organizationName := "dpratt747"

lazy val zioVersion = "2.1.22"
lazy val zioHttpVersion = "3.0.1"
lazy val zioJsonVersion = "0.7.45"
lazy val zioJdbcVersion = "0.1.2"
lazy val zioConfigVersion = "4.0.5"
lazy val testContainerScalaVersion = "0.43.6"
lazy val flywayVersion = "11.17.2"
lazy val postgresDriverVersion = "42.7.8"
lazy val h2Version = "2.4.240"
lazy val chimneyVersion = "1.8.2"
lazy val ironVersion = "3.2.1"
lazy val izumiReflectVersion = "3.0.7"

lazy val root = (project in file("."))
  .settings(
    name := "zio-app-example-scala3",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-jdbc" % zioJdbcVersion,
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "org.postgresql" % "postgresql" % postgresDriverVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,
      "io.scalaland" %% "chimney" % chimneyVersion,
      "io.github.iltotore" %% "iron" % ironVersion,
      "dev.zio" %% "izumi-reflect" % izumiReflectVersion
    ) ++ testDependencies,
    scalacOptions ++= Seq("-Yretain-trees"),
    resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
  )

lazy val integration = (project in file("integration"))
  .dependsOn(root)
  .settings(
    publish / skip := true,
    libraryDependencies ++= testDependencies
  )

lazy val testDependencies = Seq(
  "dev.zio" %% "zio-test" % zioVersion % Test,
  "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  "dev.zio" %% "zio-test-magnolia" % zioVersion % Test,
  "com.h2database" % "h2" % h2Version % Test,
  "com.dimafeng" %% "testcontainers-scala-postgresql" % testContainerScalaVersion % Test
)