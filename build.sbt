import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import sbt.Keys._

import scala.language.postfixOps
import scalariform.formatter.preferences._

name := "vamp-lifter"

version := VersionHelper.versionByTag

scalaVersion := "2.12.1"

scalariformSettings ++ Seq(ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Preserve)
  .setPreference(RewriteArrowSymbols, true))

lazy val root = project.in(sbt.file(".")).settings(packAutoSettings).settings(
  libraryDependencies += "io.vamp" %% "vamp-operation" % "katana" % "provided"
)

scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++ Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")
