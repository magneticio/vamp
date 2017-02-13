package io.vamp.container_driver.marathon

case class AppResponse(app: App)

case class AppsResponse(apps: List[App])

case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task], healthChecks: List[MarathonHealthCheck])

/**
  * A class to compare the app and marathon app for checking wheter Marathon needs to be updated based on config settings
  */
case class ComparableApp private (id: String, cpus: Double, mem: Double, healthChecks: List[MarathonHealthCheck])

object ComparableApp {
  def fromApp(app: App): ComparableApp =
    ComparableApp(app.id, app.cpus, app.mem, app.healthChecks)

  def fromMarathonApp(marathonApp: MarathonApp): ComparableApp =
    ComparableApp(marathonApp.id, marathonApp.cpus, marathonApp.mem, marathonApp.healthChecks)
}

case class Task(id: String, host: String, ports: List[Int], startedAt: Option[String])
