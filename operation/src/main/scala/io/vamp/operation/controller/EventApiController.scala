package io.vamp.operation.controller

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.event.{ Event, EventQuery, TimeRange }
import io.vamp.model.reader._
import io.vamp.operation.sse.EventStreamingActor
import io.vamp.operation.sse.EventStreamingActor.{ CloseStream, OpenStream }
import io.vamp.pulse.PulseActor.{ Publish, Query }
import io.vamp.pulse.{ EventRequestEnvelope, EventResponseEnvelope, PulseActor }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.{ existentials, postfixOps }

trait EventApiController {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  private val tagParameter = "tag"

  def publish(request: String)(implicit timeout: Timeout) = {
    val event = EventReader.read(request)
    actorFor[PulseActor] ? Publish(event) map (_ ⇒ event)
  }

  def query(parameters: Map[String, List[String]], request: String)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = {

    val query = if (request.isEmpty) {
      EventQuery(parameters.getOrElse(tagParameter, Nil).toSet, None)
    } else {
      EventQueryReader.read(request)
    }

    actorFor[PulseActor] ? Query(EventRequestEnvelope(query, page, perPage))
  }

  def openStream(channel: ActorRef, parameters: Map[String, List[String]], request: String) = {

    val tags = if (request.isEmpty) {
      parameters.getOrElse(tagParameter, Nil).toSet
    } else {
      EventQueryReader.read(request).tags
    }

    actorFor[EventStreamingActor] ! OpenStream(channel, tags)
  }

  def closeStream(to: ActorRef) = actorFor[EventStreamingActor] ! CloseStream(to)
}

trait EventValue {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  def last(tags: Set[String], window: FiniteDuration): Future[Option[AnyRef]] = {

    val eventQuery = EventQuery(tags, Option(timeRange(window)), None)

    actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
      case EventResponseEnvelope(Event(_, value, _, _) :: tail, _, _, _) ⇒ Option(value)
      case other ⇒ None
    }
  }

  protected def timeRange(window: FiniteDuration) = {

    val now = OffsetDateTime.now()
    val from = now.minus(window.toSeconds, ChronoUnit.SECONDS)

    TimeRange(Some(from), Some(now), includeLower = true, includeUpper = true)
  }
}
