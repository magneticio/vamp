package io.vamp.operation.http

import java.util.UUID

import akka.actor.ActorRef
import io.vamp.common.akka._
import io.vamp.operation.http.WebSocketActor.Action.ActionType
import io.vamp.operation.http.WebSocketActor.Content.ContentType
import io.vamp.operation.http.WebSocketActor.Status.StatusType
import io.vamp.operation.notification._

import scala.collection.mutable

object WebSocketActor {

  sealed trait SessionEvent

  case class SessionOpened(id: UUID, actor: ActorRef) extends SessionEvent

  case class SessionClosed(id: UUID) extends SessionEvent

  case class SessionRequest(id: UUID, request: Request) extends SessionEvent

  case class Request(api: String,
                     path: String,
                     action: ActionType,
                     accept: ContentType,
                     content: ContentType,
                     transaction: String,
                     data: Option[String],
                     parameters: Map[String, AnyRef] = Map())

  case class Response(api: String,
                      path: String,
                      action: ActionType,
                      status: StatusType,
                      content: ContentType,
                      transaction: String,
                      data: Option[String],
                      parameters: Map[String, AnyRef] = Map())

  object Action extends Enumeration {
    type ActionType = Value

    val Peek, Put, Remove = Value
  }

  object Content extends Enumeration {
    type ContentType = Value

    val Json, Yaml, Javascript = Value
  }

  object Status extends Enumeration {
    type StatusType = Value

    val Ok, Accepted, NoContent, Error = Value
  }

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

  private def sessionRequest(id: UUID, request: Request) = {
    log.info(s"Session request [$id]: $request")
    sessions.get(id).foreach(_ ! Response(request.api, request.path, request.action, Status.Ok, request.content, request.transaction, request.data.map(x ⇒ s"response: $x")))
  }
}
