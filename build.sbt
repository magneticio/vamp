import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scala.language.postfixOps
import scalariform.formatter.preferences._

organization in ThisBuild := "io.vamp"

name := """vamp"""

version in ThisBuild := VersionHelper.versionByTag // version based on env VAMP_VERSION and "git describe"

scalaVersion := "2.12.1"

scalaVersion in ThisBuild := scalaVersion.value

description in ThisBuild := """Vamp"""

resolvers in ThisBuild ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.sonatypeRepo("releases"),
  Resolver.jcenterRepo
)

// Libraries
val akka = "com.typesafe.akka" %% "akka-actor" % "2.4.16" ::
  "com.typesafe.akka" %% "akka-http" % "10.0.3" ::
  "com.typesafe.akka" %% "akka-parsing" % "10.0.3" ::
  ("de.heikoseeberger" %% "akka-sse" % "2.0.0" excludeAll ExclusionRule(organization = "com.typesafe.akka")) ::
  "ch.megard" %% "akka-http-cors" % "0.1.10" ::
  ("com.typesafe.akka" %% "akka-slf4j" % "2.4.16" exclude("org.slf4j", "slf4j-api")) :: Nil

val json4s = "org.json4s" %% "json4s-native" % "3.5.0" ::
  "org.json4s" %% "json4s-core" % "3.5.0" ::
  "org.json4s" %% "json4s-ext" % "3.5.0" ::
  "org.json4s" %% "json4s-native" % "3.5.0" :: Nil

val snakeYaml = "org.yaml" % "snakeyaml" % "1.16" :: Nil

val kamon = ("io.kamon" %% "kamon-core" % "0.6.4" excludeAll ExclusionRule(organization = "com.typesafe.akka")) ::
  "org.slf4j" % "jul-to-slf4j" % "1.7.21" :: Nil

val logging = "org.slf4j" % "slf4j-api" % "1.7.21" ::
  "ch.qos.logback" % "logback-classic" % "1.1.7" ::
  ("com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" exclude("org.slf4j", "slf4j-api")) :: Nil

val testing = "junit" % "junit" % "4.11" % "test" ::
  "org.scalatest" %% "scalatest" % "3.0.1" % "test" ::
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test" ::
  "com.typesafe.akka" %% "akka-testkit" % "2.4.14" % "test" :: Nil

val templating = Seq("org.jtwig" % "jtwig-core" % "5.65")

val sql = Seq(
  "org.postgresql" % "postgresql" % "9.4-1202-jdbc42",
  "mysql" % "mysql-connector-java" % "6.0.6",
  "com.microsoft.sqlserver" % "mssql-jdbc" % "6.1.0.jre8")

val fp = Seq(
  "org.typelevel" %% "cats" % "0.9.0",
  "com.chuusai"  %% "shapeless" % "2.3.2")

val redislbs = Seq("com.github.etaty" %% "rediscala" % "1.8.0")

val configlbs = Seq("com.typesafe"  % "config" % "1.3.1")

val zookeeperlbs = Seq("org.apache.zookeeper" % "zookeeper" % "3.4.8"
  exclude("org.slf4j", "slf4j-log4j12") exclude("log4j", "log4j"))

// Force scala version for the dependencies
dependencyOverrides in ThisBuild ++= Set(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-library" % scalaVersion.value
)

lazy val formatting = scalariformSettings ++ Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(CompactControlReadability, true)
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(RewriteArrowSymbols, true))

lazy val root = project.in(file(".")).settings(
  // Disable publishing root empty pom
  packagedArtifacts in RootProject(file(".")) := Map.empty,
  // allows running main classes from subprojects
  run := {
    (run in bootstrap in Compile).evaluated
  }
).aggregate(
  common,
  persistence,
  model,
  operation,
  bootstrap,
  container_driver,
  workflow_driver,
  pulse,
  http_api,
  gateway_driver,
  lifter,
  dcos,
  elasticsearch,
  config,
  haproxy,
  mysql,
  postgresql,
  redis,
  sqlserver,
  zookeeper,
  consul)

lazy val bootstrap = project.settings(packAutoSettings).settings(
  description := "Bootstrap for Vamp",
  name := "vamp-bootstrap",
  formatting
).dependsOn(common, persistence, model, operation, container_driver, workflow_driver, pulse, http_api, gateway_driver)

lazy val http_api = project.settings(
  description := "Http Api for Vamp",
  name := "vamp-http_api",
  formatting,
  libraryDependencies ++= testing
).dependsOn(operation)

lazy val operation = project.settings(
  description := "The control center of Vamp",
  name := "vamp-operation",
  formatting,
  libraryDependencies ++= testing
).dependsOn(persistence, container_driver, workflow_driver, gateway_driver, pulse)

lazy val pulse = project.settings(
  description := "Enables Vamp to connect to event storage - Elasticsearch",
  name := "vamp-pulse",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model)

lazy val gateway_driver = project.settings(
  description := "Enables Vamp to talk to Vamp Gateway Agent",
  name := "vamp-gateway_driver",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model, pulse, persistence)

lazy val container_driver = project.settings(
  description := "Enables Vamp to talk to container managers",
  name := "vamp-container_driver",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model, persistence, pulse)

lazy val workflow_driver = project.settings(
  description := "Enables Vamp to talk to workflow managers",
  name := "vamp-workflow_driver",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model, pulse, persistence, container_driver)

lazy val persistence = project.settings(
  description := "Stores Vamp artifacts",
  name := "vamp-persistence",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model, pulse)

lazy val model = project.settings(
  description := "Definitions of Vamp artifacts",
  name := "vamp-model",
  formatting,
  libraryDependencies ++= testing
).dependsOn(common)

lazy val common = project.settings(
  description := "Vamp common",
  name := "vamp-common",
  formatting,
  libraryDependencies ++= akka ++ json4s ++ snakeYaml ++ kamon ++ logging ++ testing
)

lazy val dcos = project.settings(
  description := "Container driver for DCOS and Marathon/Mesos",
  name := "vamp-dcos",
  formatting,
  libraryDependencies ++= testing
).dependsOn(pulse, workflow_driver, container_driver)

lazy val elasticsearch = project.settings(
  description := "Pulse and metrics driver for Elasticsearch",
  name := "vamp-elasticsearch",
  formatting,
  libraryDependencies ++= testing
).dependsOn(pulse, persistence)

lazy val  config = project.settings(
  description := "Typelevel config library for VAMP",
  name := "vamp-config",
  formatting,
  libraryDependencies ++= testing ++ fp ++ configlbs
).dependsOn(common)

lazy val haproxy = project.settings(
  description := "HAProxy driver",
  name := "vamp-haproxy",
  formatting,
  libraryDependencies ++= testing ++ templating
).dependsOn(gateway_driver)

lazy val lifter = project.settings(
  description := "Lifter initializes VAMP components",
  name := "vamp-lifter",
  formatting,
  libraryDependencies ++= testing ++ sql ++ fp
).dependsOn(operation, elasticsearch)

lazy val mysql = project.settings(
  description := "MySQL Driver for VAMP persistence",
  name := "vamp-mysql",
  formatting,
  libraryDependencies ++= testing ++ sql
).dependsOn(persistence)

lazy val postgresql = project.settings(
  description := "PostgreSQL driver for VAMP persistence",
  name := "vamp-postgresql",
  formatting,
  libraryDependencies ++= testing ++ sql
).dependsOn(persistence)

lazy val sqlserver = project.settings(
  description := "MS SQL Server driver for VAMP persistence",
  name := "vamp-sqlserver",
  formatting,
  libraryDependencies ++= testing
).dependsOn(persistence)

lazy val redis = project.settings(
  description := "Redis driver for VAMP",
  name := "vamp-redis",
  formatting,
  libraryDependencies ++= testing ++ redislbs
).dependsOn(persistence)

lazy val zookeeper = project.settings(
  description := "Zookeeper driver for VAMP K/V Store",
  name := "vamp-zookeeper",
  formatting,
  libraryDependencies ++= testing ++ zookeeperlbs
).dependsOn(persistence)

lazy val consul = project.settings(
  description := "Driver for Consul",
  name := "vamp-consul",
  formatting,
  libraryDependencies ++= testing
).dependsOn(persistence)

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")
