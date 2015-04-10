organization := "io.vamp"


name := """common"""

version := "0.7.0-RC3"

scalaVersion := "2.11.5"

publishMavenStyle := true

description := """This is a common set of libraries for vamp products, such as helpers, case class generators, various traits and such."""



pomExtra := (<url>http://vamp.io</url>
    <licenses>
      <license>
        <name>The Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <name>Roman Useinov</name>
        <email>roman@magnetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
      <developer>
        <name>Dragoslav Pavkovic</name>
        <email>drago@magnetic.io</email>
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
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases",
  "spray repo" at "http://repo.spray.io",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  Resolver.jcenterRepo
)

Seq(bintraySettings:_*)

val akkaV = "2.3.9"

val dispatchV = "0.11.2"

val json4sV = "3.2.11"

val vampPulseApiV =  "0.7.0+"     // or use "latest.release" for the stable version

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaV,
  "com.typesafe.akka" %% "akka-testkit" % akkaV,
  "commons-cli" % "commons-cli" % "1.2",
  "net.databinder.dispatch" %% "dispatch-core" % dispatchV,
  "net.databinder.dispatch" %% "dispatch-json4s-native" % dispatchV,
  "org.json4s" %% "json4s-core" % json4sV,
  "org.json4s" %% "json4s-ext" % json4sV,
  "org.json4s" %% "json4s-native" % json4sV,
  "org.yaml" % "snakeyaml" % "1.14",
  "org.scalatest" %% "scalatest" % "3.0.0-SNAP4" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "io.vamp" %% "pulse-api" %  vampPulseApiV
)

bintrayPublishSettings

bintray.Keys.repository in bintray.Keys.bintray := "vamp"

licenses  += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("magnetic-io")