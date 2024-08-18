ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.example"
ThisBuild / organizationName := "example"

lazy val zioVersion = "2.1.6"
lazy val zioHttpVersion = "3.0.0-RC9"
lazy val zioJsonVersion = "0.6.2"
lazy val zioMockVersion = "1.0.0-RC12"
lazy val zioPreludeVersion = "1.0.0-RC26"
lazy val testContainerScalaVersion = "0.41.4"
lazy val zioJdbcVersion = "0.1.2"
lazy val zioConfigVersion = "4.0.2"
lazy val postgresDriverVersion = "42.7.3"
lazy val flywayVersion = "10.17.1"
lazy val h2Version = "2.3.232"
lazy val chimneyVersion = "1.4.0"


lazy val root = (project in file("."))
  .settings(
    name := "zio-app-example1-scala3",
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-http" % zioHttpVersion,
      "dev.zio" %% "zio-json" % zioJsonVersion,
      "dev.zio" %% "zio-jdbc" % zioJdbcVersion,
      "dev.zio" %% "zio-config" % zioConfigVersion,
      "dev.zio" %% "zio-config-magnolia" % zioConfigVersion,
      "dev.zio" %% "zio-config-typesafe" % zioConfigVersion,
      "dev.zio" %% "zio-prelude" % zioPreludeVersion,
      "org.postgresql" % "postgresql" % postgresDriverVersion,
      "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,
      "io.scalaland" %% "chimney" % chimneyVersion
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