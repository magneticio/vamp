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
  Resolver.jcenterRepo
)

// Libraries

val akka = "com.typesafe.akka" %% "akka-actor" % "2.4.16" ::
  "com.typesafe.akka" %% "akka-http" % "10.0.1" ::
  "com.typesafe.akka" %% "akka-parsing" % "10.0.1" ::
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
  common, persistence, model, operation, bootstrap, container_driver, workflow_driver, pulse, http_api, gateway_driver
)

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

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")

