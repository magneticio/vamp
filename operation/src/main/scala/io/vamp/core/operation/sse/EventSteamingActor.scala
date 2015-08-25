package io.vamp.core.operation.sse

import akka.actor.ActorRef
import io.vamp.common.akka.{CommonSupportForActors, IoC}
import io.vamp.common.http.SseDirectives.SseMessage
import io.vamp.common.json.{OffsetDateTimeSerializer, SerializationFormat}
import io.vamp.core.model.event.Event
import io.vamp.core.operation.notification.OperationNotificationProvider
import io.vamp.core.operation.sse.EventSteamingActor.{Channel, CloseStream, OpenStream}
import io.vamp.core.pulse.Percolator.{RegisterPercolator, UnregisterPercolator}
import io.vamp.core.pulse.PulseActor
import org.json4s.native.Serialization._

object EventSteamingActor {

  case class OpenStream(channel: ActorRef, tags: Set[String])

  case class CloseStream(channel: ActorRef)

  case class Channel(channel: ActorRef)

}

class EventSteamingActor extends CommonSupportForActors with OperationNotificationProvider {

  import IoC._

  private val percolator = "stream://"

  def receive: Receive = {

    case OpenStream(channel, tags) =>
      val action = RegisterPercolator(s"$percolator$channel", tags, Channel(channel))

      actorFor[PulseActor] ! action
      actorFor[SseConsumerActor] ! action

    case CloseStream(channel) =>
      val action = UnregisterPercolator(s"$percolator$channel")

      actorFor[PulseActor] ! action
      actorFor[SseConsumerActor] ! action

    case (Channel(channel), event: Event) =>
      channel ! SseMessage(Some(event.`type`), write(event)(SerializationFormat(OffsetDateTimeSerializer)))

    case _ =>
  }
}

