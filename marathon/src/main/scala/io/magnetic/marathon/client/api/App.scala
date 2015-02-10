package io.magnetic.marathon.client.api

case class Docker(image: String, network: String, portMappings: List[AnyRef], privileged: Boolean, parameters: Map[String, AnyRef])

case class Container(`type`: String, docker: Docker, volumes: List[AnyRef])

case class UpgradeStrategy(minimumHealthCapacity: Double)

case class App(id: String, cmd: String, args: List[AnyRef], container: Container, cpus: Double, mem: Double, deployments: List[AnyRef], env: Map[String, AnyRef], executor: String, constraints: List[AnyRef], healthChecks: List[AnyRef], instances: Int, ports: List[Int], backoffSeconds: Int, backoffFactor: Double, tasksRunning: Int, tasksStaged: Int, uris: List[AnyRef], dependencies: List[AnyRef], upgradeStrategy: UpgradeStrategy, version: String)