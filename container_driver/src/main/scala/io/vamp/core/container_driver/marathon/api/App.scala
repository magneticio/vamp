package io.vamp.core.container_driver.marathon.api

case class PortMappings(containerPort: Int, hostPort: Int, servicePort: Int, protocol: String)

case class Docker(image: String, network: String, portMappings: List[PortMappings])

case class Container(`type`: String, volumes: List[Any], docker: Docker)

case class UpgradeStrategy(minimumHealthCapacity: Int)

case class Task(appId: String, id: String, host: String, ports: List[Int], startedAt: String, stagedAt: String, version: String)

case class App(id: String, cmd: AnyRef, args: List[Any], user: AnyRef, env: Map[String, AnyRef], instances: Int, cpus: Double, mem: Double, disk: Int, executor: String, constraints: List[Any], uris: List[Any], storeUrls: List[Any], ports: List[Int], requirePorts: Boolean, backoffFactor: Double, container: Container, healthChecks: List[Any], dependencies: List[Any], upgradeStrategy: UpgradeStrategy, version: String, deployments: List[Any], tasks: List[Task], lastTaskFailure: AnyRef, tasksStaged: Int, tasksRunning: Int, backoffSeconds: Int)