organization := "io.vamp"


name := """common"""

version := "0.7.10" + VersionHelper.versionSuffix

scalaVersion := "2.11.7"

publishMavenStyle := false

description := """This is a common set of libraries for vamp products, such as helpers, case class generators, various traits and such."""



pomExtra := (<url>http://vamp.io</url>
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
    <connection>scm:git:git@github.com:magneticio/vamp-common.git</connection>
    <developerConnection>scm:git:git@github.com:magneticio/vamp-common.git</developerConnection>
    <url>git@github.com:magneticio/vamp-common.git</url>
  </scm>
  )


resolvers ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.jcenterRepo
)

Seq(bintraySettings: _*)

val akkaVersion = "2.3.11"
val sprayVersion = "1.3.2"
val sprayJsonVersion = "1.3.1"
val dispatchVersion = "0.11.2"
val json4sVersion = "3.2.11"
val snakeYamlVersion = "1.14"
val commonCliVersion = "1.2"
val scalaLoggingVersion = "3.1.0"
val scalaTestVersion = "3.0.0-SNAP4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-httpx" % sprayVersion,
  "io.spray" %% "spray-json" % sprayJsonVersion,
  "io.spray" %% "spray-can" % sprayJsonVersion,
  "commons-cli" % "commons-cli" % commonCliVersion,
  "net.databinder.dispatch" %% "dispatch-core" % dispatchVersion,
  "net.databinder.dispatch" %% "dispatch-json4s-native" % dispatchVersion,
  "org.json4s" %% "json4s-core" % json4sVersion,
  "org.json4s" %% "json4s-ext" % json4sVersion,
  "org.json4s" %% "json4s-native" % json4sVersion,
  "org.yaml" % "snakeyaml" % snakeYamlVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "junit" % "junit" % "4.11" % "test"
)

bintrayPublishSettings

bintray.Keys.repository in bintray.Keys.bintray := "vamp"

licenses +=("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("magnetic-io")

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-target:jvm-1.8", "-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")