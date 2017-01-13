package io.vamp.container_driver.marathon

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model.{ HttpHeader, HttpRequest, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import de.heikoseeberger.akkasse.{ EventStreamUnmarshalling, ServerSentEvent }
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.container_driver.ContainerChangeEvent
import org.json4s.native.JsonMethods._
import org.json4s.{ DefaultFormats, StringInput }

trait MarathonSse {
  this: Actor with ActorLogging with ExecutionContextProvider ⇒

  import EventStreamUnmarshalling._

  private implicit val formats = DefaultFormats

  def openEventStream(uri: Uri): Unit = {
    implicit val actorMaterializer = ActorMaterializer()(context)
    Source.single(HttpRequest(uri = "/v2/events", headers = List(HttpHeader.parse("Accept", "text/event-stream").asInstanceOf[ParsingResult.Ok].header)))
      .via(Http()(context.system).outgoingConnection(uri.authority.host.address, uri.authority.port))
      .mapAsync(1)(Unmarshal(_).to[Source[ServerSentEvent, NotUsed]])
      .runForeach(_.runForeach { e ⇒
        e.`type`.foreach(t ⇒ e.data.foreach(d ⇒ onEvent(t → d)))
      })
  }

  private def onEvent: PartialFunction[(String, String), Unit] = {
    case (t, data) if t == "deployment_step_success" ⇒
      val ids = (parse(StringInput(data), useBigDecimalForDouble = true) \ "plan" \ "steps" \\ "actions" \ "app").extract[List[String]]
      ids.foreach { id ⇒
        log.info(s"marathon deployment event for: '$id'")
        self ! ContainerChangeEvent(id)
      }
  }
}
