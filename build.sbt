organization := "io.vamp"


name := """common"""

version := "0.7.6"

scalaVersion := "2.11.5"

publishMavenStyle := true

description := """This is a common set of libraries for vamp products, such as helpers, case class generators, various traits and such."""



pomExtra := (<url>http://vamp.io</url>
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

val akkaVersion = "2.3.11"
val sprayVersion = "1.3.2"
val sprayJsonVersion = "1.3.1"
val dispatchVersion = "0.11.2"
val json4sVersion = "3.2.11"
val vampPulseApiVersion =  "0.7.5"     // or use "latest.release" for the stable version

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
  "io.spray" %% "spray-routing" % sprayVersion,
  "io.spray" %% "spray-httpx" % sprayVersion,
  "io.spray" %% "spray-json" % sprayJsonVersion,
  "commons-cli" % "commons-cli" % "1.2",
  "net.databinder.dispatch" %% "dispatch-core" % dispatchVersion,
  "net.databinder.dispatch" %% "dispatch-json4s-native" % dispatchVersion,
  "org.json4s" %% "json4s-core" % json4sVersion,
  "org.json4s" %% "json4s-ext" % json4sVersion,
  "org.json4s" %% "json4s-native" % json4sVersion,
  "org.yaml" % "snakeyaml" % "1.14",
  "org.scalatest" %% "scalatest" % "3.0.0-SNAP4" % "test",
  "junit" % "junit" % "4.11" % "test",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "io.vamp" %% "pulse-api" %  vampPulseApiVersion
)

bintrayPublishSettings

bintray.Keys.repository in bintray.Keys.bintray := "vamp"

licenses  += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

bintray.Keys.bintrayOrganization in bintray.Keys.bintray := Some("magnetic-io")

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-target:jvm-1.8", "-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")