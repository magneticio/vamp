package io.vamp.operation.sse

import akka.actor.ActorRef
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.model.event.Event
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.operation.sse.EventStreamingActor.{ Channel, CloseStream, OpenStream }
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.PulseActor

object EventStreamingActor {

  case class OpenStream(channel: ActorRef, tags: Set[String])

  case class CloseStream(channel: ActorRef)

  case class Channel(channel: ActorRef)

}

class EventStreamingActor extends CommonSupportForActors with OperationNotificationProvider {

  def receive: Receive = {

    case OpenStream(channel, tags)        ⇒ actorFor[PulseActor] ! RegisterPercolator(percolator(channel), tags, Channel(channel))

    case CloseStream(channel)             ⇒ actorFor[PulseActor] ! UnregisterPercolator(percolator(channel))

    case (Channel(channel), event: Event) ⇒ ???

    //case (Channel(channel), event: Event) ⇒ channel ! SseMessage(Some(event.`type`), write(event)(SerializationFormat(OffsetDateTimeSerializer)))

    case _                                ⇒
  }

  private def percolator(channel: ActorRef) = s"stream://${channel.path.elements.mkString("/")}"
}
