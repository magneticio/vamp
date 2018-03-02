package io.vamp.container_driver.marathon

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.sse.ServerSentEvent
import io.vamp.common.Namespace
import io.vamp.common.http.{ SseConnector, SseListener }
import org.json4s._
import org.json4s.native.JsonMethods._

import scala.util.Try

case class MarathonSse(config: MarathonClientConfig, namespace: Namespace, listener: (String, String) ⇒ Unit) extends SseListener {

  def open()(implicit system: ActorSystem, logger: LoggingAdapter): Unit = {
    logger.info(s"Subscribing to Marathon SSE stream: ${namespace.name}")
    SseConnector.open(s"${config.marathonUrl}/v2/events", config.headers, config.tlsCheck)(this)
  }

  def close()(implicit logger: LoggingAdapter): Unit = {
    logger.info(s"Unsubscribing from Marathon SSE stream: ${namespace.name}")
    SseConnector.close(this)
  }

  final override def onEvent(event: ServerSentEvent): Unit = {
    event.eventType.filter(_.startsWith("deployment")).foreach { _ ⇒
      Try(
        (parse(StringInput(event.data), useBigDecimalForDouble = true) \ "plan" \ "steps" \\ "app" \\ classOf[JString]).toSet
      ).foreach {
        _.foreach(id ⇒ listener(event.eventType.getOrElse(""), id))
      }
    }
  }
}
