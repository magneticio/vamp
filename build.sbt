import sbt.Keys._

organization in ThisBuild := "io.vamp"

name := """core"""

version in ThisBuild := "0.7.9"+ VersionHelper.versionSuffix

scalaVersion := "2.11.7"

scalaVersion in ThisBuild := scalaVersion.value

publishMavenStyle in ThisBuild := false

// This has to be overridden for sub-modules to have different description
description in ThisBuild:= """Core is the brain of Vamp."""

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
  bintrayOrganization  := Some("magnetic-io"),
  licenses  += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayRepository  := "vamp"
)

val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.12.4" % "test"

// Library Versions

val vampCommonVersion = "0.7.9-dev.7dcadf5"
val vampUiVersion = "0.7.9-87"

val sprayVersion = "1.3.2"
val json4sVersion = "3.2.11"
val akkaVersion = "2.3.11"
val scalaLoggingVersion = "3.1.0"
val slf4jVersion = "1.7.10"
val logbackVersion = "1.1.2"
val junitVersion = "4.11"
val scalatestVersion = "2.2.4"
val typesafeConfigVersion = "1.2.1"
val scalaAsyncVersion = "0.9.2"
val snakeYamlVersion = "1.14"
val h2Version = "1.3.166"
val slickVersion = "2.1.0"
val activeSlickVersion = "0.2.2"
val postgresVersion = "9.1-901.jdbc4"
val quartzVersion = "2.2.1"
val bcprovVersion= "1.46"
val unisocketsNettyVersion = "0.1.0"
val jerseyVersion = "2.15"

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
    persistence, model, operation, bootstrap, container_driver, dictionary, pulse, rest_api, router_driver, swagger, cli
  ).disablePlugins(sbtassembly.AssemblyPlugin)


lazy val bootstrap = project.settings(bintraySetting: _*).settings(
  description := "Bootstrap for Vamp Core",
  name:="core-bootstrap",
  libraryDependencies ++= Seq(
    "org.json4s" %% "json4s-native" % json4sVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "com.typesafe" % "config" % typesafeConfigVersion
  ),
  // Runnable assembly jar lives in bootstrap/target/scala_2.11/ and is renamed to core assembly for consistent filename for
  // downloading
  assemblyJarName in assembly := s"core-assembly-${version.value}.jar"
).dependsOn(rest_api)

val downloadUI = taskKey[Unit]("Download vamp-ui to the rest_api lib directory")

lazy val rest_api = project.settings(bintraySetting: _*).settings(
  description := "REST api for Vamp Core",
  name:="core-rest_api",
  libraryDependencies ++=Seq(
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-routing" % sprayVersion,
    "io.spray" %% "spray-httpx" % sprayVersion
  ))
 .settings(
    downloadUI := {
      val libDir = "rest_api/lib"
      val targetFile = s"vamp-ui-$vampUiVersion.jar"
      // Only perform this if the file not already exists.
      if(java.nio.file.Files.notExists(new File(s"$libDir/$targetFile").toPath)) {
        // Remove old versions of the vamp-ui jar & download the new one
        IO.delete(IO.listFiles(new File(libDir)) filter ( _.getName.startsWith("vamp-ui")))
        IO.download(new URL(s"https://bintray.com/artifact/download/magnetic-io/downloads/vamp-ui/vamp-ui-$vampUiVersion.jar"), new File(s"$libDir/$targetFile"))
      }
    }
  )
  .settings((compile in Compile)<<= (compile in Compile) dependsOn downloadUI)
  .dependsOn(operation, swagger).disablePlugins(sbtassembly.AssemblyPlugin)


lazy val operation = project.settings(bintraySetting: _*).settings(
  description := "The control center of Vamp",
  name:="core-operation",
  libraryDependencies ++=Seq(
    "org.quartz-scheduler" % "quartz" % quartzVersion,
    "org.glassfish.jersey.core" % "jersey-client" % jerseyVersion,
    "org.glassfish.jersey.media" % "jersey-media-sse" % jerseyVersion
  )
).dependsOn(persistence, container_driver, dictionary, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val pulse = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to connect to event storage - Elasticsearch",
  name:="core-pulse"
).dependsOn(router_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val router_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to Vamp Router",
  name:="core-router_driver"
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val container_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to container managers",
  name:="core-container_driver",
  libraryDependencies ++=Seq(
    "org.scala-lang.modules" %% "scala-async" % scalaAsyncVersion,
    "org.bouncycastle" % "bcprov-jdk16" % bcprovVersion,
    "me.lessis" %% "unisockets-netty" % unisocketsNettyVersion
)
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val persistence = project.settings(bintraySetting: _*).settings(
  description:= "Stores Vamp artifacts",
  name:="core-persistence",
  libraryDependencies ++=Seq(
    "com.h2database" % "h2" % h2Version,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "io.strongtyped" %% "active-slick" % activeSlickVersion,
    "postgresql" % "postgresql" % postgresVersion,
    "junit" % "junit" % junitVersion % "test",
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )
).dependsOn(model, pulse).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val cli = project.settings(bintraySetting: _*).settings(
  description := "Command Line Interface for Vamp",
  name:="core-cli",
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion
  ),
  assemblyJarName in assembly := s"vamp-cli-${version.value}.jar"
).dependsOn(model)

lazy val dictionary = project.settings(bintraySetting: _*).settings(
  description := "Dictionary for Vamp",
  name:="core-dictionary"
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val model = project.settings(bintraySetting: _*).settings(
  description := "Definitions of Vamp artifacts",
  name:="core-model",
  libraryDependencies ++= Seq(
    "io.vamp" %% "common" % vampCommonVersion,
    "org.yaml" % "snakeyaml" % snakeYamlVersion,
    "junit" % "junit" % junitVersion % "test",
    "org.scalatest" %% "scalatest" % scalatestVersion % "test",
    scalaCheck
  )
).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val swagger = project.settings(bintraySetting: _*).settings(
  description := "Swagger annotations",
  name:="core-swagger"
).disablePlugins(sbtassembly.AssemblyPlugin)

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")



