import com.typesafe.sbt.SbtScalariform._
import sbt.Keys._

import scala.language.postfixOps
import scalariform.formatter.preferences._

organization in ThisBuild := "io.vamp"

name := """vamp"""

version in ThisBuild := VersionHelper.versionByTag // version based on "git describe"

scalaVersion := "2.11.8"

scalaVersion in ThisBuild := scalaVersion.value

publishMavenStyle in ThisBuild := false

// This has to be overridden for sub-modules to have different description
description in ThisBuild := """Vamp"""

pomExtra in ThisBuild := <url>http://vamp.io</url>
  <licenses>
    <license>
      <name>The Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>Dragoslav Pavkovic</name>
      <email>drago@magnetic.io</email>
      <organization>VAMP</organization>
      <organizationUrl>http://vamp.io</organizationUrl>
    </developer>
    <developer>
      <name>Matthijs Dekker</name>
      <email>matthijs@magnetic.io</email>
      <organization>VAMP</organization>
      <organizationUrl>http://vamp.io</organizationUrl>
    </developer>
  </developers>
  <scm>
    <connection>scm:git:git@github.com:magneticio/vamp.git</connection>
    <developerConnection>scm:git:git@github.com:magneticio/vamp.git</developerConnection>
    <url>git@github.com:magneticio/vamp.git</url>
  </scm>


//
resolvers in ThisBuild += Resolver.url("magnetic-io ivy resolver", url("http://dl.bintray.com/magnetic-io/vamp"))(Resolver.ivyStylePatterns)

resolvers in ThisBuild ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.jcenterRepo
)

lazy val bintraySetting = Seq(
  bintrayOrganization := Some("magnetic-io"),
  licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayRepository := "vamp"
)

// Libraries

val akka = "com.typesafe.akka" %% "akka-actor" % "2.4.9" ::
  "com.typesafe.akka" %% "akka-agent" % "2.4.9" ::
  "com.typesafe.akka" %% "akka-http-core" % "2.4.9" ::
  "com.typesafe.akka" %% "akka-http-experimental" % "2.4.9" ::
  ("de.heikoseeberger" %% "akka-sse" % "1.8.1" exclude("com.typesafe.akka", "akka-http-experimental")) ::
  ("com.typesafe.akka" %% "akka-slf4j" % "2.4.9" exclude("org.slf4j", "slf4j-api")) :: Nil

val docker = "com.spotify" % "docker-client" % "5.0.1" :: Nil

val zookeeper = ("org.apache.zookeeper" % "zookeeper" % "3.4.8" exclude("org.slf4j", "slf4j-log4j12") exclude("log4j", "log4j")) :: Nil

val async = "org.scala-lang.modules" %% "scala-async" % "0.9.2" :: Nil

val parboiled = "org.parboiled" %% "parboiled-scala" % "1.1.7" :: Nil

val jtwig = "org.jtwig" % "jtwig-core" % "5.57" :: Nil

val json4s = "org.json4s" %% "json4s-native" % "3.4.0" ::
  "org.json4s" %% "json4s-core" % "3.4.0" ::
  "org.json4s" %% "json4s-ext" % "3.4.0" ::
  "org.json4s" %% "json4s-native" % "3.4.0" :: Nil

val snakeYaml = "org.yaml" % "snakeyaml" % "1.16" :: Nil

val kamon = "io.kamon" %% "kamon-core" % "0.6.1" ::
  "org.slf4j" % "jul-to-slf4j" % "1.7.21" :: Nil

val logging = "org.slf4j" % "slf4j-api" % "1.7.21" ::
  "ch.qos.logback" % "logback-classic" % "1.1.7" ::
  ("com.typesafe.scala-logging" %% "scala-logging" % "3.1.0" exclude("org.slf4j", "slf4j-api")) :: Nil

val testing = "junit" % "junit" % "4.11" % "test" ::
  "org.scalatest" %% "scalatest" % "3.0.0-M10" % "test" ::
  "org.scalacheck" %% "scalacheck" % "1.12.4" % "test" ::
  "com.typesafe.akka" %% "akka-testkit" % "2.4.9" % "test" :: Nil

// Force scala version for the dependencies
dependencyOverrides in ThisBuild ++= Set(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-library" % scalaVersion.value
)

// Root project and subproject definitions
lazy val root = project.in(file(".")).settings(bintraySetting: _*).settings(
  // Disable publishing root empty pom
  packagedArtifacts in file(".") := Map.empty,
  // allows running main classes from subprojects
  run := {
    (run in bootstrap in Compile).evaluated
  }
).aggregate(
  common, persistence, model, operation, bootstrap, container_driver, workflow_driver, dictionary, pulse, rest_api, gateway_driver, cli
).disablePlugins(sbtassembly.AssemblyPlugin)


lazy val formatting = scalariformSettings ++ Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)
  .setPreference(RewriteArrowSymbols, true))


lazy val bootstrap = project.settings(bintraySetting: _*).settings(
  description := "Bootstrap for Vamp",
  name := "vamp-bootstrap",
  formatting,
  // Runnable assembly jar lives in bootstrap/target/scala_2.11/
  // and is renamed to vamp assembly for consistent filename for downloading.
  assemblyJarName in assembly := s"vamp-assembly-${version.value}.jar"
).dependsOn(common, persistence, model, operation, container_driver, workflow_driver, dictionary, pulse, rest_api, gateway_driver, lifter)

lazy val lifter = project.settings(bintraySetting: _*).settings(
  description := "Lifter for Vamp",
  name := "vamp-lifter",
  formatting,
  libraryDependencies ++= testing
).dependsOn(common, persistence, pulse, gateway_driver, container_driver, operation).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val rest_api = project.settings(bintraySetting: _*).settings(
  description := "REST api for Vamp",
  name := "vamp-rest_api",
  formatting,
  libraryDependencies ++= testing
).dependsOn(operation).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val operation = project.settings(bintraySetting: _*).settings(
  description := "The control center of Vamp",
  name := "vamp-operation",
  formatting,
  libraryDependencies ++= testing
).dependsOn(persistence, container_driver, workflow_driver, gateway_driver, dictionary, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val pulse = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to connect to event storage - Elasticsearch",
  name := "vamp-pulse",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val gateway_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to Vamp Gateway Agent",
  name := "vamp-gateway_driver",
  formatting,
  libraryDependencies ++= jtwig ++ testing
).dependsOn(model, pulse, persistence).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val container_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to container managers",
  name := "vamp-container_driver",
  formatting,
  libraryDependencies ++= docker ++ testing
).dependsOn(model, persistence, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val workflow_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to workflow managers",
  name := "vamp-workflow_driver",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model, pulse, persistence, container_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val persistence = project.settings(bintraySetting: _*).settings(
  description := "Stores Vamp artifacts",
  name := "vamp-persistence",
  formatting,
  libraryDependencies ++= zookeeper ++ testing
).dependsOn(model, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val cli = project.settings(bintraySetting: _*).settings(
  description := "Command Line Interface for Vamp",
  name := "vamp-cli",
  formatting,
  libraryDependencies ++= testing,
  assemblyJarName in assembly := s"vamp-cli-${version.value}.jar"
).dependsOn(model)

lazy val dictionary = project.settings(bintraySetting: _*).settings(
  description := "Dictionary for Vamp",
  name := "vamp-dictionary",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val model = project.settings(bintraySetting: _*).settings(
  description := "Definitions of Vamp artifacts",
  name := "vamp-model",
  formatting,
  libraryDependencies ++= parboiled ++ testing
).dependsOn(common).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val common = project.settings(bintraySetting: _*).settings(
  description := "Vamp common",
  name := "vamp-common",
  formatting,
  libraryDependencies ++= akka ++ json4s ++ snakeYaml ++ kamon ++ logging ++ testing
).disablePlugins(sbtassembly.AssemblyPlugin)

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")
