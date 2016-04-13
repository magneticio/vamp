import sbt._

import scala.language.postfixOps

object Dependencies {
   val dockerClientVersion = "3.5.12"
   val sprayClientVersion = "1.2.3"

   val dockerClient = "com.spotify" % "docker-client" % dockerClientVersion exclude("org.bouncycastle", "bcpkix-jdk15on")
   val sprayClient = "io.spray" % "spray-client" % sprayClientVersion
}
