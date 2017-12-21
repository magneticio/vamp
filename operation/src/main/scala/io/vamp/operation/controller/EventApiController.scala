package io.vamp.operation.controller

import akka.actor.{ ActorRef, Props }
import akka.pattern.ask
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.scaladsl.Source
import akka.util.Timeout
import de.heikoseeberger.akkasse.ServerSentEvent
import io.vamp.common.akka.IoC._
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.{ Namespace, UnitPlaceholder }
import io.vamp.model.event.{ Event, EventQuery }
import io.vamp.model.reader._
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor.{ Publish, Query }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }
import org.json4s.native.Serialization._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EventApiController extends AbstractController {

  private val tagParameter = "tag"

  private val typeParameter = "type"

  def sourceEvents(parameters: Map[String, List[String]], request: String, keepAlivePeriod: FiniteDuration)(implicit namespace: Namespace, timeout: Timeout): Source[ServerSentEvent, ActorRef] = {
    Source.actorPublisher[ServerSentEvent](Props(new ActorPublisher[ServerSentEvent] {
      def receive = {
        case Request(_) ⇒ openEventStream(self, parameters, request)
        case Cancel     ⇒ closeEventStream(self)
        case (None, event: Event) ⇒
          if (totalDemand > 0) filterSse(event).map {
            case true ⇒ onNext(ServerSentEvent(write(event)(SerializationFormat(OffsetDateTimeSerializer)), event.`type`))
            case _    ⇒
          }
        case _ ⇒
      }
    })).keepAlive(keepAlivePeriod, () ⇒ ServerSentEvent.heartbeat)
  }

  def publishEvent(request: String)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] = {
    val event = EventReader.read(request)
    (actorFor[PulseActor] ? Publish(event)).map(_ ⇒ UnitPlaceholder)
  }

  def queryEvents(parameters: Map[String, List[String]], request: String)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    val query = parseQuery(parameters, request)
    actorFor[PulseActor] ? Query(EventRequestEnvelope(request = query, page = page, perPage = perPage))
  }

  def openEventStream(to: ActorRef, parameters: Map[String, List[String]], request: String, message: Any = None)(implicit namespace: Namespace): Unit = {
    val (tags, kind) = {
      if (request.isEmpty) parameters.getOrElse(tagParameter, Nil).toSet → parameters.get(typeParameter).map(_.head)
      else {
        val query = EventQueryReader.read(request)
        query.tags → query.`type`
      }
    }

    actorFor[PulseActor].tell(RegisterPercolator(percolator(to), tags, kind, message), to)
  }

  def closeEventStream(to: ActorRef)(implicit namespace: Namespace): Unit =
    actorFor[PulseActor].tell(UnregisterPercolator(percolator(to)), to)

  protected def parseQuery(parameters: Map[String, List[String]], request: String) = {
    if (request.isEmpty) EventQuery(tags = parameters.getOrElse(tagParameter, Nil).toSet, `type` = parameters.get(typeParameter).map(_.head), timestamp = None, aggregator = None)
    else EventQueryReader.read(request)
  }

  protected def filterSse(event: Event)(implicit namespace: Namespace, timeout: Timeout): Future[Boolean] = Future.successful(true)

  private def percolator(channel: ActorRef) = s"stream://${channel.path.elements.mkString("/")}"
}