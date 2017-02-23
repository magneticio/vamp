package io.vamp.operation.controller

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import akka.pattern.ask
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.scaladsl.Source
import akka.util.Timeout
import de.heikoseeberger.akkasse.ServerSentEvent
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.model.event.{ Event, EventQuery, TimeRange }
import io.vamp.model.reader._
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor.{ Publish, Query }
import io.vamp.pulse.{ EventRequestEnvelope, EventResponseEnvelope, PulseActor }
import org.json4s.native.Serialization._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EventApiController {
  this: CommonProvider ⇒

  private val tagParameter = "tag"

  private val typeParameter = "type"

  def sourceEvents(parameters: Map[String, List[String]], request: String, keepAlivePeriod: FiniteDuration) = {
    Source.actorPublisher[ServerSentEvent](Props(new ActorPublisher[ServerSentEvent] {
      def receive = {
        case Request(_)           ⇒ openEventStream(self, parameters, request)
        case Cancel               ⇒ closeEventStream(self)
        case (None, event: Event) ⇒ if (totalDemand > 0) onNext(ServerSentEvent(write(event)(SerializationFormat(OffsetDateTimeSerializer)), event.`type`))
        case _                    ⇒

      }
    })).keepAlive(keepAlivePeriod, () ⇒ ServerSentEvent.heartbeat)
  }

  def publishEvent(request: String)(implicit timeout: Timeout) = {
    val event = EventReader.read(request)
    actorFor[PulseActor] ? Publish(event)
  }

  def queryEvents(parameters: Map[String, List[String]], request: String)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = {

    val query = {
      if (request.isEmpty) EventQuery(parameters.getOrElse(tagParameter, Nil).toSet, parameters.get(typeParameter).map(_.head), None)
      else EventQueryReader.read(request)
    }

    actorFor[PulseActor] ? Query(EventRequestEnvelope(query, page, perPage))
  }

  def openEventStream(to: ActorRef, parameters: Map[String, List[String]], request: String, message: Any = None) = {

    val (tags, kind) = {
      if (request.isEmpty) parameters.getOrElse(tagParameter, Nil).toSet → parameters.get(typeParameter).map(_.head)
      else {
        val query = EventQueryReader.read(request)
        query.tags → query.`type`
      }
    }

    actorFor[PulseActor].tell(RegisterPercolator(percolator(to), tags, kind, message), to)
  }

  def closeEventStream(to: ActorRef) = actorFor[PulseActor].tell(UnregisterPercolator(percolator(to)), to)

  private def percolator(channel: ActorRef) = s"stream://${channel.path.elements.mkString("/")}"
}

trait EventValue {
  this: CommonProvider ⇒

  implicit def timeout: Timeout

  def last(tags: Set[String], window: FiniteDuration, `type`: Option[String] = None): Future[Option[AnyRef]] = {

    val eventQuery = EventQuery(tags, `type`, Option(timeRange(window)), None)

    actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
      case EventResponseEnvelope(Event(_, _, value, _, _) :: _, _, _, _) ⇒ Option(value)
      case _ ⇒ None
    }
  }

  protected def timeRange(window: FiniteDuration) = {

    val now = OffsetDateTime.now()
    val from = now.minus(window.toSeconds, ChronoUnit.SECONDS)

    TimeRange(Some(from), Some(now), includeLower = true, includeUpper = true)
  }
}

