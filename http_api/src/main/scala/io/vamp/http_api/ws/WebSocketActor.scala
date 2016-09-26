package io.vamp.http_api.ws

import java.net.URLEncoder
import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ ContentType, ContentTypes, HttpCharsets, HttpMethods, _ }
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.http_api.notification.HttpApiNotificationProvider

import scala.collection.mutable
import scala.concurrent.Future

object WebSocketActor {

  sealed trait SessionEvent

  case class SessionOpened(id: UUID, actor: ActorRef) extends SessionEvent

  case class SessionClosed(id: UUID) extends SessionEvent

  case class SessionRequest(handler: HttpRequest ⇒ Future[HttpResponse], id: UUID, request: WebSocketMessage) extends SessionEvent

}

class WebSocketActor extends CommonSupportForActors with HttpApiNotificationProvider {

  import WebSocketActor._

  private val sessions = mutable.Map[UUID, ActorRef]()

  def receive = {
    case SessionOpened(id, actor)             ⇒ sessionOpened(id, actor)
    case SessionClosed(id)                    ⇒ sessionClosed(id)
    case SessionRequest(handler, id, request) ⇒ sessionRequest(handler, id, request)
    case _                                    ⇒
  }

  private def sessionOpened(id: UUID, actor: ActorRef) = {
    log.info(s"WebSocket session opened [$id]: $actor}")
    context.watch(actor)
    sessions += (id -> actor)
  }

  private def sessionClosed(id: UUID) = {
    log.info(s"WebSocket session closed [$id]")
    sessions.remove(id)
  }

  private def sessionRequest(handler: HttpRequest ⇒ Future[HttpResponse], id: UUID, request: WebSocketMessage) = {
    log.debug(s"WebSocket session request [$id]: $request")

    def send(response: WebSocketMessage) = sessions.get(id).foreach(_ ! response)

    request match {
      case req: WebSocketRequest ⇒ handler(asHttp(req)).map {
        case HttpResponse(s, h, HttpEntity.Strict(_, d), _) ⇒

          val status = s match {
            case StatusCodes.OK        ⇒ Status.Ok
            case StatusCodes.Accepted  ⇒ Status.Accepted
            case StatusCodes.NoContent ⇒ Status.NoContent
            case _                     ⇒ Status.Error
          }

          val params = h.map(header ⇒ header.name() -> header.value()).toMap

          val data = if (d.isEmpty) None else Option(d.utf8String)

          send(WebSocketResponse(req.api, req.path, req.action, status, req.accept, req.transaction, data, params))

        case _ ⇒
      }
      case other ⇒ send(other)
    }
  }

  private def asHttp(request: WebSocketRequest): HttpRequest = {
    new HttpRequest(
      method = toMethod(request),
      uri = toUri(request),
      headers = toHeaders(request),
      entity = toEntity(request),
      protocol = HttpProtocols.`HTTP/1.1`
    )
  }

  private def toMethod(request: WebSocketRequest): HttpMethod = request.action match {
    case Action.Peek   ⇒ HttpMethods.GET
    case Action.Put    ⇒ HttpMethods.PUT
    case Action.Remove ⇒ HttpMethods.DELETE
  }

  private def toUri(request: WebSocketRequest): Uri = {

    def encode(s: String) = URLEncoder.encode(s, "UTF-8")

    val params = if (request.parameters.nonEmpty) {
      val flatten = request.parameters.map {
        case (k, v) ⇒ s"${encode(k)}=${encode(v.toString)}"
      } mkString "&"
      s"?$flatten"
    } else ""

    Uri(if (request.path.startsWith("/")) s"${request.path}$params" else s"/${request.path}$params")
  }

  private def toHeaders(request: WebSocketRequest): List[HttpHeader] = (request.accept match {
    case Content.PlainText  ⇒ Accept(`text/plain`)
    case Content.Json       ⇒ Accept(`application/json`)
    case Content.Javascript ⇒ Accept(`application/javascript`)
    case Content.Yaml       ⇒ Accept(HttpApiDirectives.`application/x-yaml`)
  }) :: Nil

  private def toEntity(request: WebSocketRequest): RequestEntity = {
    val `type`: ContentType = request.content match {
      case Content.PlainText  ⇒ ContentTypes.`text/plain(UTF-8)`
      case Content.Json       ⇒ ContentTypes.`application/json`
      case Content.Javascript ⇒ ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
      case Content.Yaml       ⇒ ContentType(HttpApiDirectives.`application/x-yaml`)
    }
    HttpEntity(`type`, request.data.getOrElse("").getBytes("UTF-8"))
  }
}
