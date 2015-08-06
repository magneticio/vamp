package io.vamp.core.operation.sse

import akka.actor.{ActorRef, Props}
import io.vamp.common.akka.{ActorDescription, CommonSupportForActors}
import io.vamp.common.http.SseDirectives.SseMessage
import io.vamp.core.model.event.Event
import io.vamp.core.operation.notification.OperationNotificationProvider
import io.vamp.core.operation.sse.EventSteamingActor.{Channel, CloseStream, OpenStream}
import io.vamp.core.pulse.PulseActor
import io.vamp.core.pulse.PulseActor.{RegisterPercolator, UnregisterPercolator}
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

object EventSteamingActor extends ActorDescription {
  def props(args: Any*): Props = Props[EventSteamingActor]

  case class OpenStream(channel: ActorRef, tags: Set[String])

  case class CloseStream(channel: ActorRef)

  case class Channel(channel: ActorRef)

}

class EventSteamingActor extends CommonSupportForActors with OperationNotificationProvider {

  private val percolator = "stream://"

  def receive: Receive = {

    case OpenStream(channel, tags) => actorFor(PulseActor) ! RegisterPercolator(s"$percolator$channel", tags, Channel(channel))

    case CloseStream(channel) => actorFor(PulseActor) ! UnregisterPercolator(s"$percolator$channel")

    case (Channel(channel), event: Event) => channel ! SseMessage(Some(event.`type`), write(event)(DefaultFormats))

    case _ =>
  }
}
