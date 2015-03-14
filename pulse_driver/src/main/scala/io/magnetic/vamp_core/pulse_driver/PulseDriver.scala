package io.magnetic.vamp_core.pulse_driver

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import io.magnetic.vamp_common.http.RestClient
import io.magnetic.vamp_core.model.artifact.{Deployment, DeploymentCluster}

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster): Future[OffsetDateTime]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Int): Future[Int]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseDriver {
  protected implicit val executionContext = ec

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster) = Future {
    OffsetDateTime.now().minus(1, ChronoUnit.HOURS)
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Int) = RestClient.request[Any](s"GET $url/api/v1/events/get").map(result => result.asInstanceOf[Map[String, BigInt]].get("value").get.toInt)
}
