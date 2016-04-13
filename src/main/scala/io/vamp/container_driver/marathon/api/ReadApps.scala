package io.vamp.container_driver.marathon.api

case class AppResponse(app: App)

case class AppsResponse(apps: List[App])

case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])

case class Task(id: String, host: String, ports: List[Int], startedAt: Option[String])
