package io.vamp.core.pulse_driver

import java.time.OffsetDateTime

import io.vamp.common.pulse.{Average, Count, PulseClient}
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Port}
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.router_driver.DefaultRouterDriverNameMatcher
import io.vamp.pulse.api._

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def event(event: Event): Unit

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Future[Boolean]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime): Future[Option[Double]]

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseClient(url) with PulseDriver with Average with Count with DefaultRouterDriverNameMatcher {
  protected implicit val executionContext = ec

  def event(event: Event) = sendEvent(event)

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime) =
    count(SlaEvent.slaTags(deployment, cluster), from, OffsetDateTime.now()).map(count => count.value > 0)

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime) =
    average("route" :: clusterRouteName(deployment, cluster, port) :: "backend" :: "rtime" :: Nil, from, to).map(average => Some(average.value))

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime) =
    getEvents(SlaEvent.slaTags(deployment, cluster), from, to).map(events => events.flatMap { event =>
      if (Escalate.tags.forall(event.tags.contains)) Escalate(deployment, cluster, event.timestamp) :: Nil
      else if (DeEscalate.tags.forall(event.tags.contains)) DeEscalate(deployment, cluster, event.timestamp) :: Nil
      else Nil
    })
}
