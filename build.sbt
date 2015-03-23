import _root_.sbt.Keys._



organization := "io.vamp"


name := """Core"""

version := "0.7.0-RC1"

scalaVersion := "2.11.5"

scalaVersion in ThisBuild := scalaVersion.value



publishMavenStyle := true

description := """Core is a module that brings all the bits and pieces of the ecosystem together taking care of the VAMP workflow"""




pomExtra := (<url>http://vamp.io</url>
    <licenses>
      <license>
        <name>The Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <name>Dragoslav Pavkovic</name>
        <email>drago@mangetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
      <developer>
        <name>Roman Useinov</name>
        <email>roman@mangetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:git@github.com:magneticio/vamp-core.git</connection>
      <developerConnection>scm:git:git@github.com:magneticio/vamp-core.git</developerConnection>
      <url>git@github.com:magneticio/vamp-core.git</url>
    </scm>
)

resolvers in ThisBuild += Resolver.mavenLocal

// Shared dependencies

// Library Versions
val sprayVersion = "1.3.2"
val sprayJsonVersion = "1.3.1"
val json4sVersion = "3.2.11"
val akkaVersion = "2.3.9"
val akkaStreamsVersion = "1.0-M3"
val scalaLoggingVersion = "3.1.0"
val slf4jVersion = "1.7.10"
val logbackVersion = "1.1.2"
val junitVersion = "4.11"
val scalatestVersion = "2.2.4"
val vampCommonVersion = "0.7.0-RC1"

// Note ThisBuild, this is what makes these dependencies shared
libraryDependencies in ThisBuild ++= Seq(
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-httpx" % sprayVersion,
  "io.spray" %% "spray-json" % sprayJsonVersion,
  "io.spray" %% "spray-testkit" % sprayVersion,
  "org.json4s" %% "json4s-native" % json4sVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamsVersion,
  "io.vamp" %% "common" % vampCommonVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "junit" % "junit" % junitVersion,
  "org.scalatest" %% "scalatest" % scalatestVersion
)



// Sub-project dependency versions
val configVersion = "1.2.1"
val snakeyamlVersion = "1.14"
val h2Version = "1.3.166"
val slf4jNopVersion = "1.6.4"
val slickVersion = "2.1.0"
val activeSlickVersion = "0.2.2"
val postgresVersion = "9.1-901.jdbc4"


// Force scala version for the dependencies
dependencyOverrides in ThisBuild += "org.scala-lang" % "scala-compiler" % scalaVersion.value

dependencyOverrides in ThisBuild += "org.scala-lang" % "scala-library" % scalaVersion.value


// Root project and subproject definitions
lazy val root = project.in(file(".")).settings(
  // allows running main classes from subprojects
  run := {
    (run in bootstrap in Compile).evaluated
  }
).aggregate(
  persistence, model, operation, bootstrap, container_driver, dictionary, pulse_driver, rest_api, router_driver, swagger
)

lazy val persistence = project.settings(
  libraryDependencies ++=Seq(
    "com.h2database" % "h2" % h2Version,
    "org.slf4j" % "slf4j-nop" % slf4jNopVersion,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "io.strongtyped" %% "active-slick" % activeSlickVersion,
    "postgresql" % "postgresql" % postgresVersion
  )
).dependsOn(model)

lazy val model = project.settings(
  libraryDependencies ++= Seq(
    "org.yaml" % "snakeyaml" % snakeyamlVersion
  )
)

lazy val operation = project.dependsOn(model, persistence, container_driver, dictionary, router_driver, pulse_driver)

lazy val bootstrap = project.settings(
  libraryDependencies ++= Seq(
    "com.typesafe" % "config" % configVersion
   )
).dependsOn(persistence, container_driver, router_driver, pulse_driver, rest_api, dictionary)

lazy val container_driver = project.dependsOn(model)

lazy val dictionary = project.dependsOn(persistence)

lazy val pulse_driver = project.dependsOn(model)

lazy val rest_api = project.dependsOn(operation, model, swagger)

lazy val router_driver = project.dependsOn(model)

lazy val swagger = project







// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")






