package io.vamp.operation.sse

import akka.actor.ActorRef
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.http.SseDirectives.SseMessage
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.model.event.Event
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.operation.sse.EventStreamingActor.{ Channel, CloseStream, OpenStream }
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor
import org.json4s.native.Serialization._

object EventStreamingActor {

  case class OpenStream(channel: ActorRef, tags: Set[String])

  case class CloseStream(channel: ActorRef)

  case class Channel(channel: ActorRef)

}

class EventStreamingActor extends CommonSupportForActors with OperationNotificationProvider {

  private val percolator = "stream://"

  def receive: Receive = {

    case OpenStream(channel, tags) ⇒
      val action = RegisterPercolator(s"$percolator$channel", tags, Channel(channel))

      actorFor[PulseActor] ! action
      actorFor[SseConsumerActor] ! action

    case CloseStream(channel) ⇒
      val action = UnregisterPercolator(s"$percolator$channel")

      actorFor[PulseActor] ! action
      actorFor[SseConsumerActor] ! action

    case (Channel(channel), event: Event) ⇒
      channel ! SseMessage(Some(event.`type`), write(event)(SerializationFormat(OffsetDateTimeSerializer)))

    case _ ⇒
  }
}

