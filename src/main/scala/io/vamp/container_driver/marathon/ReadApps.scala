package io.vamp.container_driver.marathon

import io.vamp.model.artifact.ServiceHealth

case class AppResponse(app: App)

case class AppsResponse(apps: List[App])

case class App(
  id: String,
  instances: Int,
  cpus: Double,
  mem: Double,
  tasks: List[Task],
  healthChecks: List[MarathonHealthCheck],
  taskStats: Option[MarathonTaskStats])

/**
  * A class to compare the app and marathon app for checking whether Marathon needs to be updated based on config settings
  */
case class ComparableApp private (
  id: String,
  instances: Int,
  cpus: Double,
  mem: Double,
  healthChecks: List[MarathonHealthCheck])

object ComparableApp {
  def fromApp(app: App): ComparableApp =
    ComparableApp(app.id, app.instances, app.cpus, app.mem, app.healthChecks)

  def fromMarathonApp(marathonApp: MarathonApp): ComparableApp =
    ComparableApp(marathonApp.id, marathonApp.instances, marathonApp.cpus, marathonApp.mem, marathonApp.healthChecks)
}

case class Task(id: String, host: String, ports: List[Int], startedAt: Option[String])

case class MarathonTaskStats(totalSummary: MarathonSummary)

case class MarathonSummary(stats: MarathonStats)

case class MarathonStats(counts: MarathonCounts)

case class MarathonCounts(staged: Int, running: Int, healthy: Int, unhealthy: Int)

object MarathonCounts {

  /** Transforms a MarathonCounts to the generic ServiceHealth */
  def toServiceHealth(marathonCounts: MarathonCounts): ServiceHealth =
    ServiceHealth(marathonCounts.staged, marathonCounts.running, marathonCounts.healthy, marathonCounts.unhealthy)

}
