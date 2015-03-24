package io.vamp.core.pulse_driver

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import io.vamp.common.http.RestClient
import io.vamp.common.pulse.PulseClient
import io.vamp.common.pulse.api.Event
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster}
import io.vamp.core.model.notification.SlaEvent

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def event(event: Event): Future[Event]

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster): Future[OffsetDateTime]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Long): Future[Long]

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseClient(url) with PulseDriver {
  protected implicit val executionContext = ec

  def event(event: Event) = Future {
    event
  }

  def lastSlaEventTimestamp(deployment: Deployment, cluster: DeploymentCluster) = Future {
    OffsetDateTime.now().minus(1, ChronoUnit.HOURS)
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, period: Long) =
    RestClient.request[Any](s"GET $url/api/v1/events/get").map(result => result.asInstanceOf[Map[String, BigInt]].get("value").get.toLong)

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime) = Future {
    Nil
  }
}
