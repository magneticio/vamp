package io.vamp.container_driver.marathon

import io.vamp.model.artifact.Health

case class AppResponse(app: App)

case class AppsResponse(apps: List[App])

case class App(
  id:           String,
  instances:    Int,
  cpus:         Double,
  mem:          Double,
  container:    Option[AppContainer]      = None,
  env:          Map[String, String]       = Map(),
  cmd:          Option[String]            = None,
  tasks:        List[Task]                = Nil,
  healthChecks: List[MarathonHealthCheck] = Nil,
  taskStats:    Option[MarathonTaskStats] = None,
  networks:     List[AppNetwork]          = Nil
)

case class AppContainer(docker: Option[DockerAppContainer], portMappings: List[DockerAppContainerPort])

case class DockerAppContainer(image: String, network: Option[String], portMappings: List[DockerAppContainerPort])

case class DockerAppContainerPort(containerPort: Option[Int], hostPort: Option[Int], servicePort: Option[Int])

case class AppNetwork(mode: String, name: Option[String])

case class Task(id: String, ipAddresses: List[TaskIpAddress], host: String, ports: List[Int], startedAt: Option[String])

case class TaskIpAddress(ipAddress: String, protocol: String)

case class MarathonTaskStats(totalSummary: MarathonSummary)

case class MarathonSummary(stats: MarathonStats)

case class MarathonStats(counts: MarathonCounts)

case class MarathonCounts(staged: Int, running: Int, healthy: Int, unhealthy: Int)

object MarathonCounts {

  /** Transforms a MarathonCounts to the generic ServiceHealth */
  def toServiceHealth(marathonCounts: MarathonCounts): Health =
    Health(marathonCounts.staged, marathonCounts.running, marathonCounts.healthy, marathonCounts.unhealthy)
}
