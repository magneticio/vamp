package io.vamp.core.pulse_driver

import java.time.OffsetDateTime

import io.vamp.common.pulse.PulseClient
import io.vamp.common.pulse.api.{Aggregator, Event, EventQuery, TimeRange}
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Port}
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.router_driver.DefaultRouterDriverNameMatcher

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def event(event: Event): Future[Event]

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Future[Boolean]

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime): Future[Long]

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]]
}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseClient(url) with PulseDriver with DefaultRouterDriverNameMatcher {
  protected implicit val executionContext = ec

  def event(event: Event) = sendEvent(event)

  def exists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime) = {
    getEvents(EventQuery(SlaEvent.slaTags(deployment, cluster), TimeRange(from))).map {
      case list: List[_] => list.nonEmpty
      case _ => false
    }
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime) = {
    val tags = "route" :: clusterRouteName(deployment, cluster, port) :: "backend" :: "rtime" :: Nil
    getEvents(EventQuery(tags, TimeRange(from, to), Some(Aggregator("average")))).map {
      case result =>
        //println(result)
        1000
    }
  }

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime) = {
    getEvents(EventQuery(SlaEvent.slaTags(deployment, cluster), TimeRange(from, to))).map {
      case list: List[_] => list.asInstanceOf[List[Event]].flatMap { event =>
        if (Escalate.tags.forall(event.tags.contains)) {
          Escalate(deployment, cluster, event.timestamp) :: Nil
        } else if (DeEscalate.tags.forall(event.tags.contains)) {
          DeEscalate(deployment, cluster, event.timestamp) :: Nil
        } else Nil
      }
      case _ => Nil
    }
  }
}
