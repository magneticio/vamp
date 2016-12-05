package io.vamp.container_driver.marathon

import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkasse.ServerSentEvent
import io.vamp.common.akka.ExecutionContextProvider
import org.json4s.native.JsonMethods._
import org.json4s.{ DefaultFormats, StringInput }

case class MarathonEvent(`type`: String, ids: List[String])

trait MarathonSse {
  this: Actor with ExecutionContextProvider ⇒

  import de.heikoseeberger.akkasse.client.EventStreamUnmarshalling._

  def sse(uri: Uri): Unit = {

    implicit val formats = DefaultFormats
    implicit val actorMaterializer = ActorMaterializer()(context)

    Source.single(HttpRequest(uri = "/v2/events", headers = List(HttpHeader.parse("Accept", "text/event-stream").asInstanceOf[ParsingResult.Ok].header)))
      .via(Http()(context.system).outgoingConnection(uri.authority.host.address, uri.authority.port))
      .mapAsync(1)(Unmarshal(_).to[Source[ServerSentEvent, Any]])
      .runForeach(_.runForeach { event ⇒
        for {
          t ← event.eventType
          d ← event.data
        } yield self ! MarathonEvent(
          t, (parse(StringInput(d), useBigDecimalForDouble = true) \ "plan" \ "steps" \\ "actions" \ "app").extract[List[String]]
        )
      })
  }
}
