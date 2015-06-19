package io.vamp.core.pulse_driver

import java.time.OffsetDateTime

import _root_.io.vamp.common.akka._
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Port}
import io.vamp.core.model.notification.{DeEscalate, Escalate, SlaEvent}
import io.vamp.core.pulse_driver.notification.{PulseDriverNotificationProvider, PulseResponseError, UnsupportedPulseDriverRequest}
import io.vamp.core.router_driver.DefaultRouterDriverNameMatcher
import io.vamp.pulse.client.PulseAggregationProvider
import io.vamp.pulse.model.Event

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object PulseDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.pulse-driver.response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[PulseDriverActor], args: _*)

  trait PulseDriverMessage

  case class Publish(event: Event) extends PulseDriverMessage

  case class EventExists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime) extends PulseDriverMessage

  case class ResponseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime) extends PulseDriverMessage

  case class QuerySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime) extends PulseDriverMessage

  case class RegisterPercolator(name: String, tags: Set[String], message: Any) extends PulseDriverMessage

  case class UnregisterPercolator(name: String) extends PulseDriverMessage

}

class PulseDriverActor extends PulseDriver with Percolator with CommonReplyActor with PulseDriverNotificationProvider {

  import io.vamp.core.pulse_driver.PulseDriverActor._

  implicit val timeout = PulseDriverActor.timeout

  val pulseUrl = ConfigFactory.load().getString("vamp.core.pulse-driver.url")

  override protected def requestType: Class[_] = classOf[PulseDriverMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case InfoRequest => offload(pulseClient.info, classOf[PulseResponseError])

      case Publish(event) => (percolate andThen publish)(Event.expandTags(event))

      case EventExists(deployment, cluster, from) => offload(eventExists(deployment, cluster, from), classOf[PulseResponseError])

      case ResponseTime(deployment, cluster, port, from, to) => offload(responseTime(deployment, cluster, port, from, to), classOf[PulseResponseError])

      case QuerySlaEvents(deployment, cluster, from, to) => offload(querySlaEvents(deployment, cluster, from, to), classOf[PulseResponseError])

      case RegisterPercolator(name, tags, message) => registerPercolator(name, tags, message)

      case UnregisterPercolator(name) => unregisterPercolator(name)

      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => exception(PulseResponseError(e))
  }
}

trait PulseDriver extends PulseAggregationProvider with DefaultRouterDriverNameMatcher {
  this: ExecutionContextProvider =>

  def publish: (Event => Unit) = { (event: Event) => pulseClient.sendEvent(event) }

  def eventExists(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime): Future[Boolean] = {
    count(SlaEvent.slaTags(deployment, cluster), Some(from), Some(OffsetDateTime.now())).map(count => count.value > 0)
  }

  def responseTime(deployment: Deployment, cluster: DeploymentCluster, port: Port, from: OffsetDateTime, to: OffsetDateTime): Future[Option[Double]] = {
    average(Set(s"routes:${clusterRouteName(deployment, cluster, port)}", "metrics:rtime"), Some(from), Some(to)).map(average => Some(average.value))
  }

  def querySlaEvents(deployment: Deployment, cluster: DeploymentCluster, from: OffsetDateTime, to: OffsetDateTime): Future[List[SlaEvent]] = {
    pulseClient.getEvents(SlaEvent.slaTags(deployment, cluster), Some(from), Some(to)).map(_.flatMap { event =>
      if (Escalate.tags.forall(event.tags.contains)) Escalate(deployment, cluster, event.timestamp) :: Nil
      else if (DeEscalate.tags.forall(event.tags.contains)) DeEscalate(deployment, cluster, event.timestamp) :: Nil
      else Nil
    })
  }
}

case class PercolatorEntry(tags: Set[String], actor: ActorRef, message: Any)

trait Percolator {
  this: Actor with ActorLogging =>

  private val percolators = mutable.Map[String, PercolatorEntry]()

  def registerPercolator(name: String, tags: Set[String], message: Any) = {
    log.info(s"Registering percolator '$name' for tags '${tags.mkString(", ")}'.")
    percolators.put(name, PercolatorEntry(tags, sender(), message))
  }

  def unregisterPercolator(name: String) = {
    if (percolators.remove(name).nonEmpty)
      log.info(s"Percolator successfully removed for '$name'.")
  }

  def percolate: (Event => Event) = { (event: Event) =>
    percolators.foreach { case (name, percolator) =>
      if (percolator.tags.forall(event.tags.contains)) {
        log.debug(s"Percolate match for '$name'.")
        percolator.actor ! (percolator.message -> event.tags)
      }
    }
    event
  }
}

