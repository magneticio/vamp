import _root_.sbt.Keys._



organization in ThisBuild := "io.vamp.core"


name := """core"""

version in ThisBuild := "0.7.0-RC4"

scalaVersion := "2.11.5"

scalaVersion in ThisBuild := scalaVersion.value


publishMavenStyle := true

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
        <name>Roman Useinov</name>
        <email>roman@magnetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:git@github.com:magneticio/vamp-core.git</connection>
      <developerConnection>scm:git:git@github.com:magneticio/vamp-core.git</developerConnection>
      <url>git@github.com:magneticio/vamp-core.git</url>
    </scm>

// Use local maven repository
resolvers in ThisBuild ++= Seq(
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  Resolver.jcenterRepo
)

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
val vampCommonVersion = "0.7.0.24"
val vampPulseApiVersion = "0.7.0.23"
val tugboatVersion = "0.2.0.1"

// Note ThisBuild, this is what makes these dependencies shared
libraryDependencies in ThisBuild ++= Seq(
  "io.spray" %% "spray-can" % sprayVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-httpx" % sprayVersion,
  "io.spray" %% "spray-json" % sprayJsonVersion,
  "io.spray" %% "spray-testkit" % sprayVersion % "test",
  "org.json4s" %% "json4s-native" % json4sVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamsVersion,
  "io.vamp" %% "common" % vampCommonVersion,
  "io.vamp" %% "pulse-api" % vampPulseApiVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.slf4j" % "slf4j-api" % slf4jVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion,
  "junit" % "junit" % junitVersion % "test",
  "org.scalatest" %% "scalatest" % scalatestVersion % "test",
  "io.vamp" %% "tugboat" % tugboatVersion
)

// Sub-project dependency versions
val configVersion = "1.2.1"
val snakeyamlVersion = "1.14"
val h2Version = "1.3.166"
val slickVersion = "2.1.0"
val activeSlickVersion = "0.2.2"
val postgresVersion = "9.1-901.jdbc4"


// Force scala version for the dependencies
dependencyOverrides in ThisBuild ++= Set(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-library" % scalaVersion.value
)

// Root project and subproject definitions
lazy val root = project.in(file(".")).settings(
  // Disable publishing root empty pom
  packagedArtifacts in file(".") := Map.empty,
  // allows running main classes from subprojects
  run := {
    (run in bootstrap in Compile).evaluated
  }
).aggregate(
  persistence, model, operation, bootstrap, container_driver, dictionary, pulse_driver, rest_api, router_driver, swagger
).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val persistence = project.settings(
  libraryDependencies ++=Seq(
    "com.h2database" % "h2" % h2Version,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "io.strongtyped" %% "active-slick" % activeSlickVersion,
    "postgresql" % "postgresql" % postgresVersion
  )
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val model = project.settings(
  libraryDependencies ++= Seq(
    "org.yaml" % "snakeyaml" % snakeyamlVersion
  )
)

lazy val operation = project.dependsOn(model, persistence, container_driver, dictionary, router_driver, pulse_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val bootstrap = project.settings(
  libraryDependencies ++= Seq(
    "com.typesafe" % "config" % configVersion
   ),
  // Runnable assembly jar lives in bootstrap/target/scala_2.11/ and is renamed to core assembly for consistent filename for
  // downloading
  assemblyJarName in assembly := s"core-assembly-${version.value}.jar"
).dependsOn(persistence, container_driver, router_driver, pulse_driver, rest_api, dictionary)

lazy val container_driver = project.dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val dictionary = project.dependsOn(persistence).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val pulse_driver = project.dependsOn(model, router_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val rest_api = project.dependsOn(operation, model, swagger).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val router_driver = project.dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val swagger = project.disablePlugins(sbtassembly.AssemblyPlugin)





// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation) ++
 Seq(
  "-feature",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps"
).filter(
    Function.const(scalaVersion.value.startsWith("2.11")))

javacOptions ++= Seq("-encoding", "UTF-8")






