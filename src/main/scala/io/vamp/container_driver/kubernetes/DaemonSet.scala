package io.vamp.container_driver.kubernetes

import io.vamp.container_driver.Docker

case class DaemonSet(name: String, docker: Docker, cpu: Double, mem: Int, args: List[String] = Nil)
