package io.magnetic.marathon.client.api

case class Docker(image: String, network: String, portMappings: List[AnyRef])

case class Container(`type`: String, volumes: List[Any], docker: Docker)

case class UpgradeStrategy(minimumHealthCapacity: Int)

case class App(id: String, cmd: AnyRef, args: List[Any], user: AnyRef, env: Map[String, AnyRef], instances: Int, cpus: Int, mem: Int, disk: Int, executor: String, constraints: List[Any], uris: List[Any], storeUrls: List[Any], ports: List[Int], requirePorts: Boolean, backoffFactor: Double, container: Container, healthChecks: List[Any], dependencies: List[Any], upgradeStrategy: UpgradeStrategy, version: String, deployments: List[Any], tasks: List[AnyRef], lastTaskFailure: AnyRef, tasksStaged: Int, tasksRunning: Int, backoffSeconds: Int)