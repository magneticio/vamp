package io.vamp.container_driver.marathon.api

import io.vamp.container_driver.ContainerPortMapping

case class MarathonApp(id: String, container: Option[Container], instances: Int, cpus: Double, mem: Double, env: Map[String, String], cmd: Option[String])

case class Container(docker: Docker, `type`: String = "DOCKER")

case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

case class DockerParameter(key: String, value: String)
