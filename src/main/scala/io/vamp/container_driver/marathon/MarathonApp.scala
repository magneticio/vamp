package io.vamp.container_driver.marathon

import io.vamp.container_driver.Docker
import io.vamp.model.artifact.{HealthCheck, Port}

import scala.util.{Left, Right, Try}

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
  port: Option[Int],
  portIndex: Option[Int],
  protocol: String,
  gracePeriodSeconds: Int,
  intervalSeconds: Int,
  timeoutSeconds: Int,
  maxConsecutiveFailures: Int)

object MarathonHealthCheck {

  /** Transforms a HealthCheck to a Marathon specific HealthCheck */
  def apply(ports: List[Port], healthCheck: HealthCheck): MarathonHealthCheck = {
    val portOrIndex: Either[Int, Int] = Try(Left(healthCheck.port.toInt))
      .getOrElse { Right(
       ports
         .zipWithIndex
         .find { case (p, i) => p.name.contains(healthCheck.port) || p.alias.contains(healthCheck.port) }
         .get // Able to get due to validation
         ._2
      )}

    portOrIndex match {
      case Left(port)   =>
        MarathonHealthCheck(
          healthCheck.path,
          Some(port),
          None,
          healthCheck.protocol,
          healthCheck.initialDelay.value,
          healthCheck.interval.value,
          healthCheck.timeout.value,
          healthCheck.failures)
      case Right(index) =>
        MarathonHealthCheck(
          healthCheck.path,
          None,
          Some(index),
          healthCheck.protocol,
          healthCheck.initialDelay.value,
          healthCheck.interval.value,
          healthCheck.timeout.value,
          healthCheck.failures)
    }
  }


}