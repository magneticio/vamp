package io.vamp.container_driver.marathon.api

import io.vamp.container_driver.docker.DockerPortMapping

case class MarathonApp(id: String, container: Option[Container], instances: Int, cpus: Double, mem: Double, env: Map[String, String], cmd: Option[String])

case class Container(docker: Docker, `type`: String = "DOCKER")

case class Docker(image: String, portMappings: List[DockerPortMapping], parameters: List[DockerParameter], privileged: Boolean, network: String = "BRIDGE")

case class DockerParameter(key: String, value: String)
