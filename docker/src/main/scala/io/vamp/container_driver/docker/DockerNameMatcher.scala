package io.vamp.container_driver.docker

import io.vamp.common.Artifact
import io.vamp.common.util.HashUtil

trait DockerNameMatcher {

  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def artifactName2Id(artifact: Artifact): String = string2Id(artifact.name)

  protected def string2Id(string: String): String = string match {
    case idMatcher(_*) if string.length < 64 ⇒ string
    case _                                   ⇒ HashUtil.hexSha1(string).substring(0, 32)
  }
}
