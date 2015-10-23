package io.vamp.operation.controller

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.reader._
import io.vamp.operation.sse.EventSteamingActor
import io.vamp.operation.sse.EventSteamingActor.{ CloseStream, OpenStream }
import io.vamp.pulse.PulseActor.{ Publish, Query }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }

trait EventApiController {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def publish(request: String)(implicit timeout: Timeout) = {
    val event = EventReader.read(request)
    actorFor[PulseActor] ? Publish(event) map (_ ⇒ event)
  }

  def query(request: String)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = {
    actorFor[PulseActor] ? Query(EventRequestEnvelope(EventQueryReader.read(request), page, perPage))
  }

  def openStream(to: ActorRef, tags: Set[String]) = actorFor[EventSteamingActor] ! OpenStream(to, tags)

  def closeStream(to: ActorRef) = actorFor[EventSteamingActor] ! CloseStream(to)
}
