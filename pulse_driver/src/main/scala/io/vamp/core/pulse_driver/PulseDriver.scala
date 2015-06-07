package io.vamp.core.pulse_driver

import java.time.OffsetDateTime

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Port}
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.router_driver.DefaultRouterDriverNameMatcher
import io.vamp.pulse.client.PulseAggregationProvider
import io.vamp.pulse.model.Event

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def info: Future[Any]

  def event(event: Event): Unit

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Future[Boolean]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime): Future[Option[Double]]

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseAggregationProvider with PulseDriver with ExecutionContextProvider with DefaultRouterDriverNameMatcher {

  implicit val executionContext = ec

  val pulseUrl = url

  def info: Future[Any] = pulseClient.info

  def event(event: Event) = pulseClient.sendEvent(event)

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime) =
    count(SlaEvent.slaTags(deployment, cluster), Some(from), Some(OffsetDateTime.now())).map(count => count.value > 0)

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime) =
    average(Set(s"routes:${clusterRouteName(deployment, cluster, port)}", "metrics:rtime"), Some(from), Some(to)).map(average => Some(average.value))

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime) =
    pulseClient.getEvents(SlaEvent.slaTags(deployment, cluster), Some(from), Some(to)).map(_.flatMap { event =>
      if (Escalate.tags.forall(event.tags.contains)) Escalate(deployment, cluster, event.timestamp) :: Nil
      else if (DeEscalate.tags.forall(event.tags.contains)) DeEscalate(deployment, cluster, event.timestamp) :: Nil
      else Nil
    })
}
