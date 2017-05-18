
organization := "io.vamp"

name := "vamp-config"

version := VersionHelper.versionByTag

scalaVersion := "2.12.1"

resolvers += Resolver.sonatypeRepo("releases")

lazy val root = project.in(sbt.file(".")).settings(packAutoSettings ++ Seq(packExcludeJars := Seq("scala-.*\\.jar"))).settings(
  libraryDependencies ++= Seq(
    "com.typesafe"  % "config"     % "1.3.1",
     "com.chuusai"  %% "shapeless" % "2.3.2",
    "org.scalatest" %% "scalatest" % "3.0.1" % "test",
    "junit"         % "junit"      % "4.11"  % "test",
    "org.typelevel" %% "cats"      % "0.9.0"
  )
)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")