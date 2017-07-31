package io.vamp.http_api.ws

import java.util.UUID

import akka.actor.PoisonPill
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers, TerminateFlowStage }
import io.vamp.common.{ Config, Namespace }
import io.vamp.http_api.ws.WebSocketActor.{ SessionClosed, SessionEvent, SessionOpened, SessionRequest }
import io.vamp.http_api.{ AbstractRoute, LogDirective }

import scala.concurrent.Future

trait WebSocketRoute extends AbstractRoute with WebSocketMarshaller with HttpApiHandlers {
  this: HttpApiDirectives with LogDirective ⇒

  implicit def materializer: Materializer

  private lazy val limit = Config.int("vamp.http-api.websocket.stream-limit")

  protected def websocketApiHandler(implicit namespace: Namespace, timeout: Timeout): Route

  def websocketRoutes(implicit namespace: Namespace, timeout: Timeout) = {
    pathEndOrSingleSlash {
      get {
        extractRequest { request ⇒
          handleWebSocketMessages {
            websocket(request)
          }
        }
      }
    }
  }

  protected def filterWebSocketOutput(message: AnyRef)(implicit namespace: Namespace, timeout: Timeout): Future[Boolean] = Future.successful(true)

  private def apiHandler(implicit namespace: Namespace, timeout: Timeout) = Route.asyncHandler(log {
    websocketApiHandler
  })

  private def websocket(origin: HttpRequest)(implicit namespace: Namespace, timeout: Timeout): Flow[AnyRef, Message, Any] = {
    val id = UUID.randomUUID()

    val in = Flow[AnyRef].collect {
      case TextMessage.Strict(message)  ⇒ Future.successful(message)
      case TextMessage.Streamed(stream) ⇒ stream.limit(limit()).completionTimeout(timeout.duration).runFold("")(_ + _)
    }.mapAsync(parallelism = 3)(identity)
      .mapConcat(unmarshall)
      .map(SessionRequest(apiHandler, id, origin, _))
      .to(Sink.actorRef[SessionEvent](actorFor[WebSocketActor], SessionClosed(id)))

    val out = Source.actorRef[AnyRef](16, OverflowStrategy.dropHead)
      .mapMaterializedValue(actorFor[WebSocketActor] ! SessionOpened(id, _))
      .via(new TerminateFlowStage[AnyRef](_ == PoisonPill))
      .mapAsync(parallelism = 3)(message ⇒ filterWebSocketOutput(message).map(f ⇒ f → message))
      .collect { case (true, m) ⇒ m }
      .map(message ⇒ TextMessage.Strict(marshall(message)))

    Flow.fromSinkAndSource(in, out)
  }
}
