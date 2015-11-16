package io.vamp.container_driver.docker

case class DockerPortMapping(containerPort: Int, protocol: String = "tcp", hostPort: Int = 0)
