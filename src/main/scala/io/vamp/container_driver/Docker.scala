package io.vamp.container_driver

case class DockerPortMapping(containerPort: Int, protocol: String = "tcp", hostPort: Int = 0)

case class Docker(image: String, portMappings: List[DockerPortMapping], parameters: List[DockerParameter], privileged: Boolean = false, network: String = "BRIDGE")

case class DockerParameter(key: String, value: String)

case class DockerApp(
  id: String,
  container: Option[Docker],
  instances: Int,
  cpu: Double,
  memory: Int,
  environmentVariables: Map[String, String],
  command: List[String] = Nil,
  arguments: List[String] = Nil,
  labels: Map[String, String] = Map(),
  constraints: List[List[String]] = Nil)