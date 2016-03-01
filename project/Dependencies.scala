import sbt._

import scala.language.postfixOps

object Dependencies {
   val dockerClientVersion = "3.5.12"

   val dockerClient = "com.spotify" % "docker-client" % dockerClientVersion

}
