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

  case class SessionRequest(id: UUID, request: WebSocketRequest) extends SessionEvent

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
    log.info(s"Session opened [$id]: $actor}")
    context.watch(actor)
    sessions += (id -> actor)
  }

  private def sessionClosed(id: UUID) = {
    log.info(s"Session closed [$id]")
    sessions.remove(id)
  }

  private def sessionRequest(id: UUID, request: WebSocketRequest) = {
    log.info(s"Session request [$id]: $request")
    sessions.get(id).foreach(_ ! WebSocketResponse(request.api, request.path, request.action, Status.Ok, request.content, request.transaction, request.data.map(x ⇒ s"response: $x")))
  }
}
