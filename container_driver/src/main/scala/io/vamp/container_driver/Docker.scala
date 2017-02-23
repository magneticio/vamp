package io.vamp.container_driver

import io.vamp.common.Config

case class DockerPortMapping(containerPort: Int, protocol: String = "tcp", hostPort: Int = 0)

object Docker {
  val network = Config.string("vamp.container-driver.network")
}

case class Docker(image: String, portMappings: List[DockerPortMapping], parameters: List[DockerParameter], privileged: Boolean = false, network: String)

case class DockerParameter(key: String, value: String)

case class DockerApp(
  id:                   String,
  container:            Option[Docker],
  instances:            Int,
  cpu:                  Double,
  memory:               Int,
  environmentVariables: Map[String, String],
  command:              List[String]        = Nil,
  arguments:            List[String]        = Nil,
  labels:               Map[String, String] = Map(),
  constraints:          List[List[String]]  = Nil
)
