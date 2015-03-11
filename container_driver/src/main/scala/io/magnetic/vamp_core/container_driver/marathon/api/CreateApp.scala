package io.magnetic.vamp_core.container_driver.marathon.api

case class CreatePortMappings(containerPort: Int, protocol: String = "tcp", hostPort: Int = 0)

case class CreateDocker(image: String, portMappings: List[CreatePortMappings], network: String = "BRIDGE")

case class CreateContainer(docker: CreateDocker, `type`: String = "DOCKER")

case class CreateApp(id: String, container: CreateContainer, instances: Int, cpus: Double, mem: Double, env: Map[String, String])
