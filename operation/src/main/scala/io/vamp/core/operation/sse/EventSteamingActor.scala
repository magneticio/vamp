package io.vamp.core.operation.sse

import akka.actor.{ActorRef, Props}
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.{ActorDescription, CommonSupportForActors}
import io.vamp.core.model.event.EventQuery
import io.vamp.core.operation.notification.OperationNotificationProvider
import io.vamp.core.operation.sse.EventSteamingActor.OpenStream


object EventSteamingActor extends ActorDescription {
  def props(args: Any*): Props = Props[EventSteamingActor]

  case class OpenStream(channel: ActorRef, query: EventQuery)
}

class EventSteamingActor extends CommonSupportForActors with OperationNotificationProvider {

  def receive: Receive = {

    case OpenStream(channel, query) =>

      channel ! "test 1"

      Thread.sleep(1000)

      channel ! "test 2"

      Thread.sleep(1000)

      channel ! "test 3"

    case Start =>

    case Shutdown =>

    case _ =>
  }
}
