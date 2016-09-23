package io.vamp.operation.controller

import java.util.UUID

import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.operation.http.WebSocketActor._
import io.vamp.operation.http.{ WebSocketActor, WebSocketMarshaller, WebSocketMessage }

trait WebSocketController extends WebSocketMarshaller {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  def websocket: Flow[AnyRef, Message, Any] = {

    val id = UUID.randomUUID()

    val in = Flow[AnyRef].collect {
      case TextMessage.Strict(message) ⇒ message
    }.mapConcat(unmarshall)
      .map(SessionRequest(id, _))
      .to(Sink.actorRef[SessionEvent](actorFor[WebSocketActor], SessionClosed(id)))

    val out = Source.actorRef[WebSocketMessage](16, OverflowStrategy.dropHead)
      .mapMaterializedValue(actorFor[WebSocketActor] ! SessionOpened(id, _))
      .map(message ⇒ TextMessage.Strict(marshall(message)))

    Flow.fromSinkAndSource(in, out)
  }
}
