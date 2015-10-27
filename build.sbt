import com.typesafe.sbt.SbtScalariform._
import sbt.Keys._

import scalariform.formatter.preferences._

organization in ThisBuild := "io.vamp"

name := """vamp"""

version in ThisBuild := "0.7.11"+ VersionHelper.versionSuffix

scalaVersion := "2.11.7"

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
    <connection>scm:git:git@github.com:magneticio/vamp-core.git</connection>
    <developerConnection>scm:git:git@github.com:magneticio/vamp-core.git</developerConnection>
    <url>git@github.com:magneticio/vamp-core.git</url>
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

val vampUi = "vamp-ui-0.7.11-145.jar"

val akka = "com.typesafe.akka" %% "akka-slf4j" % "2.4.0" :: Nil
val spray = "io.spray" %% "spray-can" % "1.3.1" ::
  "io.spray" %% "spray-routing" % "1.3.2" ::
  "io.spray" %% "spray-httpx" % "1.3.2" ::
  "io.spray" %% "spray-json" % "1.3.1" :: Nil

val async = "org.scala-lang.modules" %% "scala-async" % "0.9.2" :: Nil
val bouncycastle = "org.bouncycastle" % "bcprov-jdk16" % "1.46" :: Nil
val unisocketsNetty = "me.lessis" %% "unisockets-netty" % "0.1.0" :: Nil
val quartz = "org.quartz-scheduler" % "quartz" % "2.2.1" :: Nil
val zookeeper = ("org.apache.zookeeper" % "zookeeper" % "3.4.6" exclude("org.slf4j", "slf4j-log4j12")) :: Nil

val sql = "com.h2database" % "h2" % "1.3.166" ::
  "com.typesafe.slick" %% "slick" % "2.1.0" ::
  "io.strongtyped" %% "active-slick" % "0.2.2" ::
  "postgresql" % "postgresql" % "9.1-901.jdbc4" :: Nil

val dispatch = "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" ::
  "net.databinder.dispatch" %% "dispatch-json4s-native" % "0.11.2" :: Nil
val twirl = "com.typesafe.play" %% "twirl-api" % "1.1.1" :: Nil
val json4s = "org.json4s" %% "json4s-native" % "3.2.11" ::
  "org.json4s" %% "json4s-core" % "3.2.11" ::
  "org.json4s" %% "json4s-ext" % "3.2.11" ::
  "org.json4s" %% "json4s-native" % "3.2.11" :: Nil
val snakeYaml = "org.yaml" % "snakeyaml" % "1.14" :: Nil
val jersey = "org.glassfish.jersey.core" % "jersey-client" % "2.15" ::
  "org.glassfish.jersey.media" % "jersey-media-sse" % "2.15" :: Nil

val config = "com.typesafe" % "config" % "1.2.1" :: Nil
val logging = "org.slf4j" % "slf4j-api" % "1.7.10" ::
  "ch.qos.logback" % "logback-classic" % "1.1.2" ::
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0" :: Nil

val testing = "junit" % "junit" % "4.11" % "test" ::
  "org.scalatest" %% "scalatest" % "3.0.0-M10" % "test" ::
  "org.scalacheck" %% "scalacheck" % "1.12.4" % "test" ::
  "com.typesafe.akka" %% "akka-testkit" % "2.4.0" % "test" :: Nil

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
  common, persistence, model, operation, bootstrap, container_driver, dictionary, pulse, rest_api, gateway_driver, cli
).disablePlugins(sbtassembly.AssemblyPlugin)


lazy val formatting = scalariformSettings ++ Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(PreserveDanglingCloseParenthesis, true)
  .setPreference(RewriteArrowSymbols, true))


lazy val bootstrap = project.settings(bintraySetting: _*).settings(
  description := "Bootstrap for Vamp",
  name := "bootstrap",
  formatting,
  // Runnable assembly jar lives in bootstrap/target/scala_2.11/ and is renamed to vamp assembly for consistent filename for
  // downloading
  assemblyJarName in assembly := s"vamp-assembly-${version.value}.jar"
).dependsOn(rest_api)

val downloadUI = taskKey[Unit]("Download vamp-ui to the rest_api lib directory")

lazy val rest_api = project.settings(bintraySetting: _*).settings(
  description := "REST api for Vamp",
  name := "rest_api",
  formatting,
  libraryDependencies ++= testing
).settings(
  downloadUI := {
    val libDir = "rest_api/lib"
    // Only perform this if the file not already exists.
    if (java.nio.file.Files.notExists(new File(s"$libDir/$vampUi").toPath)) {
      // Remove old versions of the vamp-ui jar & download the new one
      IO.delete(IO.listFiles(new File(libDir)) filter (_.getName.startsWith("vamp-ui")))
      IO.download(new URL(s"https://bintray.com/artifact/download/magnetic-io/downloads/vamp-ui/$vampUi"), new File(s"$libDir/$vampUi"))
    }
  }
).settings((compile in Compile) <<= (compile in Compile) dependsOn downloadUI)
  .dependsOn(operation).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val operation = project.settings(bintraySetting: _*).settings(
  description := "The control center of Vamp",
  name := "operation",
  formatting,
  libraryDependencies ++= quartz ++ jersey ++ testing
).dependsOn(persistence, container_driver, gateway_driver, dictionary, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val pulse = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to connect to event storage - Elasticsearch",
  name := "pulse",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val gateway_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to Vamp Gateway Agent",
  name := "gateway_driver",
  formatting,
  libraryDependencies ++= twirl ++ zookeeper ++ testing
).dependsOn(model, pulse).enablePlugins(SbtTwirl).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val container_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to container managers",
  name := "container_driver",
  formatting,
  libraryDependencies ++= async ++ bouncycastle ++ unisocketsNetty ++ testing
).dependsOn(model, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val persistence = project.settings(bintraySetting: _*).settings(
  description := "Stores Vamp artifacts",
  name := "persistence",
  formatting,
  libraryDependencies ++= sql ++ testing
).dependsOn(model, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val cli = project.settings(bintraySetting: _*).settings(
  description := "Command Line Interface for Vamp",
  name := "cli",
  formatting,
  libraryDependencies ++= testing,
  assemblyJarName in assembly := s"vamp-cli-${version.value}.jar"
).dependsOn(model)

lazy val dictionary = project.settings(bintraySetting: _*).settings(
  description := "Dictionary for Vamp",
  name := "dictionary",
  formatting,
  libraryDependencies ++= testing
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val model = project.settings(bintraySetting: _*).settings(
  description := "Definitions of Vamp artifacts",
  name := "model",
  formatting,
  libraryDependencies ++= testing
).dependsOn(common).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val common = project.settings(bintraySetting: _*).settings(
  description := "Vamp common",
  name := "common",
  formatting,
  libraryDependencies ++= akka ++ spray ++ dispatch ++ json4s ++ snakeYaml ++ logging ++ testing
).disablePlugins(sbtassembly.AssemblyPlugin)

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")

