package io.vamp.container_driver.marathon

import io.vamp.container_driver.Docker

case class MarathonApp(
  id: String,
  container: Option[Container],
  instances: Int,
  cpus: Double,
  mem: Int,
  env: Map[String, String],
  cmd: Option[String],
  args: List[String] = Nil,
  labels: Map[String, String] = Map(),
  constraints: List[List[String]] = Nil)

case class Container(docker: Docker, `type`: String = "DOCKER")