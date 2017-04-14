package io.vamp.operation.controller

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import akka.actor.{ ActorRef, Props }
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.scaladsl.Source
import ch.qos.logback.classic.spi.ILoggingEvent
import de.heikoseeberger.akkasse.ServerSentEvent
import io.vamp.common.Namespace
import io.vamp.common.akka._
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import org.json4s.native.Serialization._

import scala.concurrent.duration.FiniteDuration

case class LogEvent(logger: String, level: String, message: String, timestamp: OffsetDateTime)

trait LogApiController extends AbstractController {

  private val eventType = "log"

  def sourceLog(level: String, logger: Option[String], keepAlivePeriod: FiniteDuration)(implicit namespace: Namespace) = {
    Source.actorPublisher[ServerSentEvent](Props(new ActorPublisher[ServerSentEvent] {
      def receive = {
        case Request(_) ⇒ openLogStream(self, level, logger, { event ⇒
          ServerSentEvent(write(encode(event))(SerializationFormat(OffsetDateTimeSerializer)), eventType)
        })
        case Cancel                                  ⇒ closeLogStream(self)
        case sse: ServerSentEvent if totalDemand > 0 ⇒ onNext(sse)
        case _                                       ⇒
      }

    })).keepAlive(keepAlivePeriod, () ⇒ ServerSentEvent.heartbeat)
  }

  def openLogStream(to: ActorRef, level: String, logger: Option[String], encoder: (ILoggingEvent) ⇒ AnyRef)(implicit namespace: Namespace) = {
    LogPublisherHub.subscribe(to, level, logger, encoder)
  }

  def closeLogStream(to: ActorRef) = LogPublisherHub.unsubscribe(to)

  def encode(loggingEvent: ILoggingEvent) = LogEvent(
    loggingEvent.getLoggerName,
    loggingEvent.getLevel.toString,
    loggingEvent.getFormattedMessage,
    OffsetDateTime.ofInstant(new Date(loggingEvent.getTimeStamp).toInstant, ZoneId.of("UTC"))
  )
}
