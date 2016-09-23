package io.vamp.operation.http

import java.util.UUID

import akka.actor.ActorRef
import io.vamp.common.akka._
import io.vamp.operation.notification._

import scala.collection.mutable

object WebSocketActor {

  sealed trait SessionEvent

  case class SessionOpened(id: UUID, actor: ActorRef) extends SessionEvent

  case class SessionClosed(id: UUID) extends SessionEvent

  case class SessionRequest(id: UUID, request: WebSocketMessage) extends SessionEvent

}

class WebSocketActor extends CommonSupportForActors with OperationNotificationProvider {

  import WebSocketActor._

  private val sessions = mutable.Map[UUID, ActorRef]()

  def receive = {
    case SessionOpened(id, actor)    ⇒ sessionOpened(id, actor)
    case SessionClosed(id)           ⇒ sessionClosed(id)
    case SessionRequest(id, request) ⇒ sessionRequest(id, request)
    case _                           ⇒
  }

  private def sessionOpened(id: UUID, actor: ActorRef) = {
    log.debug(s"WebSocket session opened [$id]: $actor}")
    context.watch(actor)
    sessions += (id -> actor)
  }

  private def sessionClosed(id: UUID) = {
    log.debug(s"WebSocket session closed [$id]")
    sessions.remove(id)
  }

  private def sessionRequest(id: UUID, request: WebSocketMessage) = {
    log.debug(s"WebSocket session request [$id]: $request")
    val response = request match {
      case WebSocketRequest(api, path, action, accept, content, transaction, data, _) ⇒
        WebSocketResponse(api, path, action, Status.Error, accept, transaction, Option("Not implemented"), Map())
      case _ ⇒ request
    }
    sessions.get(id).foreach(_ ! response)
  }
}
