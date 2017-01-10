package io.vamp.http_api.ws

import java.net.URLEncoder
import java.util.UUID

import akka.actor.{ ActorRef, PoisonPill }
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ ContentType, ContentTypes, HttpCharsets, HttpMethods, HttpResponse, _ }
import io.vamp.common.akka._
import io.vamp.common.http.HttpApiDirectives
import io.vamp.http_api.notification.HttpApiNotificationProvider
import io.vamp.operation.controller.EventApiController

import scala.collection.mutable
import scala.concurrent.Future

object WebSocketActor {

  sealed trait SessionEvent

  case class SessionOpened(id: UUID, actor: ActorRef) extends SessionEvent

  case class SessionClosed(id: UUID) extends SessionEvent

  case class SessionRequest(apiHandler: HttpRequest ⇒ Future[HttpResponse], id: UUID, request: WebSocketMessage) extends SessionEvent

}

class WebSocketActor extends EventApiController with CommonSupportForActors with HttpApiNotificationProvider {

  import WebSocketActor._

  private val sessions = mutable.Map[UUID, ActorRef]()

  def receive = {
    case SessionOpened(id, actor)             ⇒ sessionOpened(id, actor)
    case SessionClosed(id)                    ⇒ sessionClosed(id)
    case SessionRequest(handler, id, request) ⇒ sessionRequest(handler, id, request)
    case _                                    ⇒
  }

  override def postStop() = {
    log.info("Shutting down WebSocket connections.")
    sessions.foreach {
      case (id, actor) ⇒
        actor ! PoisonPill
        sessionClosed(id)
    }
  }

  private def sessionOpened(id: UUID, actor: ActorRef) = {
    log.info(s"WebSocket session opened [$id]: $actor}")
    context.watch(actor)
    sessions += (id → actor)
  }

  private def sessionClosed(id: UUID) = {
    log.info(s"WebSocket session closed [$id]")
    sessions.remove(id).foreach(closeStream)
  }

  private def sessionRequest(apiHandler: HttpRequest ⇒ Future[HttpResponse], id: UUID, request: WebSocketMessage) = {
    log.debug(s"WebSocket session request [$id]: $request")
    request match {
      case req: WebSocketRequest ⇒ handle(id, req, apiHandler)
      case other                 ⇒ sessions.get(id).foreach(_ ! other)
    }
  }

  private def handle(id: UUID, request: WebSocketRequest, apiHandler: HttpRequest ⇒ Future[HttpResponse]) = sessions.get(id).foreach { receiver ⇒

    if (request.eventStream) {

      val params = request.parameters.filter {
        case (_, v: List[_]) ⇒ v.forall(_.isInstanceOf[String])
        case _               ⇒ false
      }.asInstanceOf[Map[String, List[String]]]

      val message = WebSocketResponse(request.api, request.path, request.action, Status.Ok, request.accept, request.transaction, None, Map())

      openStream(receiver, params, request.data.getOrElse(""), message)

    }
    else {

      val httpRequest = new HttpRequest(toMethod(request), toUri(request), toHeaders(request), toEntity(request), HttpProtocols.`HTTP/1.1`)

      apiHandler(httpRequest).map {
        case response: HttpResponse ⇒ toResponse(request, response).foreach(receiver ! _)
        case _                      ⇒
      }
    }
  }

  private def toMethod(request: WebSocketRequest): HttpMethod = request.action match {
    case Action.Peek   ⇒ HttpMethods.GET
    case Action.Put    ⇒ if (request.path.split(WebSocketMessage.pathDelimiter).length == 2) HttpMethods.POST else HttpMethods.PUT
    case Action.Remove ⇒ HttpMethods.DELETE
  }

  private def toUri(request: WebSocketRequest): Uri = {

    def encode(s: String) = URLEncoder.encode(s, "UTF-8")

    val params = if (request.parameters.nonEmpty) {
      val flatten = request.parameters.map {
        case (k, v) ⇒ s"${encode(k)}=${encode(v.toString)}"
      } mkString "&"
      s"?$flatten"
    }
    else ""

    Uri(s"${request.path}$params")
  }

  private def toHeaders(request: WebSocketRequest): List[HttpHeader] = (request.accept match {
    case Content.PlainText  ⇒ Accept(`text/plain`)
    case Content.Json       ⇒ Accept(`application/json`)
    case Content.Javascript ⇒ Accept(`application/javascript`)
    case Content.Yaml       ⇒ Accept(HttpApiDirectives.`application/x-yaml`)
  }) :: Nil

  private def toEntity(request: WebSocketRequest): RequestEntity = {
    val `type` = request.content match {
      case Content.PlainText  ⇒ ContentTypes.`text/plain(UTF-8)`
      case Content.Json       ⇒ ContentTypes.`application/json`
      case Content.Javascript ⇒ ContentType(MediaTypes.`application/javascript`, HttpCharsets.`UTF-8`)
      case Content.Yaml       ⇒ ContentType(HttpApiDirectives.`application/x-yaml`)
    }
    HttpEntity(`type`, request.data.getOrElse("").getBytes("UTF-8"))
  }

  private def toResponse(request: WebSocketRequest, response: HttpResponse): Option[WebSocketResponse] = response.entity match {

    case HttpEntity.Strict(_, d) ⇒

      val status = response.status match {
        case StatusCodes.OK        ⇒ Status.Ok
        case StatusCodes.Created   ⇒ Status.Ok
        case StatusCodes.Accepted  ⇒ Status.Accepted
        case StatusCodes.NoContent ⇒ Status.NoContent
        case _                     ⇒ Status.Error
      }

      val params = response.headers.map(header ⇒ header.name() → header.value()).toMap
      val data = if (d.isEmpty) None else Option(d.utf8String)

      Option(WebSocketResponse(request.api, request.path, request.action, status, request.accept, request.transaction, data, params))

    case _ ⇒ None
  }
}
