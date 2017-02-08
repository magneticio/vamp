package io.vamp.container_driver.marathon

import io.vamp.container_driver.Docker
import io.vamp.model.artifact.HealthCheck

case class MarathonApp(
  id:          String,
  container:   Option[Container],
  instances:   Int,
  cpus:        Double,
  mem:         Int,
  env:         Map[String, String],
  cmd:         Option[String],
  healthChecks: List[MarathonHealthCheck] = Nil,
  args:        List[String]        = Nil,
  labels:      Map[String, String] = Map(),
  constraints: List[List[String]]  = Nil)

case class Container(docker: Docker, `type`: String = "DOCKER")

case class MarathonHealthCheck(
  path: String,
  port: Int,
  protocol: String,
  gracePeriodSeconds: Int,
  intervalSeconds: Int,
  timeoutSeconds: Int,
  maxConsecutiveFailures: Int,
  ignoreHttp1xx: Boolean)

object MarathonHealthCheck {

  /** Transforms a HealthCheck to a Marathon specific HealthCheck */
  def apply(healthCheck: HealthCheck): MarathonHealthCheck =
    MarathonHealthCheck(
      healthCheck.path,
      healthCheck.port.toInt, // Fix with conversion etc.
      healthCheck.protocol,
      healthCheck.initialDelay.value,
      healthCheck.interval.value,
      healthCheck.timeout.value,
      healthCheck.failures,
      ignoreHttp1xx = false) // Fix standard?

}