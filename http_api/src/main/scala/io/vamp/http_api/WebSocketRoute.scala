package io.vamp.http_api

import java.util.UUID

import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.{ Materializer, OverflowStrategy }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers }
import io.vamp.common.notification.NotificationProvider
import io.vamp.http_api.ws.WebSocketActor.{ SessionClosed, SessionEvent, SessionOpened, SessionRequest }
import io.vamp.http_api.ws.{ WebSocketActor, WebSocketMarshaller, WebSocketMessage }

import scala.concurrent.Future

trait WebSocketRoute extends WebSocketMarshaller with HttpApiHandlers {
  this: HttpApiDirectives with ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  implicit def materializer: Materializer

  def restfulRoutes: Route

  private def handler: HttpRequest ⇒ Future[HttpResponse] = Route.asyncHandler(restfulRoutes)

  val websocketRoutes = {
    get {
      handleWebSocketMessages {
        websocket
      }
    }
  }

  private def websocket: Flow[AnyRef, Message, Any] = {

    val id = UUID.randomUUID()

    val in = Flow[AnyRef].collect {
      case TextMessage.Strict(message) ⇒ message
    }.mapConcat(unmarshall)
      .map(SessionRequest(handler, id, _))
      .to(Sink.actorRef[SessionEvent](actorFor[WebSocketActor], SessionClosed(id)))

    val out = Source.actorRef[WebSocketMessage](16, OverflowStrategy.dropHead)
      .mapMaterializedValue(actorFor[WebSocketActor] ! SessionOpened(id, _))
      .map(message ⇒ TextMessage.Strict(marshall(message)))

    Flow.fromSinkAndSource(in, out)
  }
}
