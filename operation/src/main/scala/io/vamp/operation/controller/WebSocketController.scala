package io.vamp.operation.controller

import java.util.UUID

import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.Artifact
import io.vamp.operation.http.WebSocketActor
import io.vamp.operation.http.WebSocketActor._
import org.json4s.DefaultFormats
import org.json4s.native.Serialization._

trait WebSocketController {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  def parallelism: Int

  private implicit val format = DefaultFormats

  def websocket: Flow[AnyRef, Message, Any] = {

    val id = UUID.randomUUID()

    val in = Flow[AnyRef].collect {
      case TextMessage.Strict(message) ⇒ SessionRequest(id, Request(Artifact.version, "/", WebSocketActor.Action.Put, WebSocketActor.Content.Yaml, WebSocketActor.Content.Yaml, "transaction:xxx", Option(message.toString)))
    }.to(Sink.actorRef[SessionEvent](actorFor[WebSocketActor], SessionClosed(id)))

    val out = Source.actorRef[AnyRef](10, OverflowStrategy.dropHead)
      .mapMaterializedValue(actorFor[WebSocketActor] ! SessionOpened(id, _)).map(message ⇒ TextMessage.Strict(write(message)))

    Flow.fromSinkAndSource(in, out)
  }
}
