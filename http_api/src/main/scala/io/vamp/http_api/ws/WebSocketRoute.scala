package io.vamp.http_api.ws

import java.util.UUID

import akka.actor.PoisonPill
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.http.scaladsl.server.Route
import akka.stream._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.util.Timeout
import io.vamp.common.Config
import io.vamp.common.akka.CommonProvider
import io.vamp.common.akka.IoC._
import io.vamp.common.http.{ HttpApiDirectives, HttpApiHandlers, TerminateFlowStage }
import io.vamp.http_api.LogDirective
import io.vamp.http_api.ws.WebSocketActor.{ SessionClosed, SessionEvent, SessionOpened, SessionRequest }

import scala.concurrent.Future

trait WebSocketRoute extends WebSocketMarshaller with HttpApiHandlers {
  this: HttpApiDirectives with LogDirective with CommonProvider ⇒

  implicit def materializer: Materializer

  def timeout: Timeout

  def websocketApiHandler: Route

  val websocketRoutes = {
    get {
      handleWebSocketMessages {
        websocket
      }
    }
  }

  private val limit = Config.int("vamp.http-api.websocket.stream-limit")()

  private val apiHandler = Route.asyncHandler(log {
    websocketApiHandler
  })

  private def websocket: Flow[AnyRef, Message, Any] = {

    val id = UUID.randomUUID()

    val in = Flow[AnyRef].collect {
      case TextMessage.Strict(message)  ⇒ Future.successful(message)
      case TextMessage.Streamed(stream) ⇒ stream.limit(limit).completionTimeout(timeout.duration).runFold("")(_ + _)
    }.mapAsync(parallelism = 3)(identity)
      .mapConcat(unmarshall)
      .map(SessionRequest(apiHandler, id, _))
      .to(Sink.actorRef[SessionEvent](actorFor[WebSocketActor], SessionClosed(id)))

    val out = Source.actorRef[AnyRef](16, OverflowStrategy.dropHead)
      .mapMaterializedValue(actorFor[WebSocketActor] ! SessionOpened(id, _))
      .via(new TerminateFlowStage[AnyRef](_ == PoisonPill))
      .map(message ⇒ TextMessage.Strict(marshall(message)))

    Flow.fromSinkAndSource(in, out)
  }
}
