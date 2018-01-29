package io.vamp.container_driver.marathon

import akka.actor.{ Actor, ActorSystem }
import akka.http.scaladsl.model.sse.ServerSentEvent
import io.vamp.common.NamespaceProvider
import io.vamp.common.akka.CommonActorLogging
import io.vamp.common.http.SseConnector
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.util.Try

trait MarathonSse {
  this: MarathonCache with Actor with CommonActorLogging with NamespaceProvider ⇒

  def openSseStream(url: String, headers: List[(String, String)], tlsCheck: Boolean)(implicit system: ActorSystem): Unit = {
    log.info(s"Subscribing to Marathon SSE stream: $self")
    SseConnector.open(url, headers, tlsCheck)(self, log.info)
  }

  def closeSseStream(): Unit = {
    log.info(s"Unsubscribing from Marathon SSE stream: $self")
    SseConnector.close(self)
  }

  def process(event: ServerSentEvent): Unit = event.eventType.filter(_.startsWith("deployment")).foreach { _ ⇒
    Try(
      (parse(StringInput(event.data), useBigDecimalForDouble = true) \ "plan" \ "steps" \\ "app" \\ classOf[JString]).toSet
    ).foreach {
      _.filter(inCache).foreach { id ⇒
        log.info(s"Processing SSE: $id")
        invalidateCache(id)
      }
    }
  }
}
