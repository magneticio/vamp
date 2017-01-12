package io.vamp.operation.controller

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import akka.actor.{ ActorRef, Props }
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.scaladsl.Source
import ch.qos.logback.classic.spi.ILoggingEvent
import de.heikoseeberger.akkasse.ServerSentEvent
import io.vamp.common.akka._
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.notification.NotificationProvider
import org.json4s.native.Serialization._

import scala.concurrent.duration.FiniteDuration

case class LogEvent(logger: String, level: String, message: String, timestamp: OffsetDateTime)

trait LogApiController {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  private val eventType = "log"

  def sourceLog(level: String, logger: Option[String], keepAlivePeriod: FiniteDuration) = {
    Source.actorPublisher[ServerSentEvent](Props(new ActorPublisher[ServerSentEvent] {
      def receive = {
        case Request(_)                              ⇒ openLogStream(self, level, logger)
        case Cancel                                  ⇒ closeLogStream(self)
        case event: ILoggingEvent if totalDemand > 0 ⇒ onNext(encoder(event))
        case _                                       ⇒
      }

    })).keepAlive(keepAlivePeriod, () ⇒ ServerSentEvent.heartbeat)
  }

  private def openLogStream(to: ActorRef, level: String, logger: Option[String]) = LogPublisherHub.subscribe(to, level, logger)

  private def closeLogStream(to: ActorRef) = LogPublisherHub.unsubscribe(to)

  private def encoder(loggingEvent: ILoggingEvent) = {
    val message = LogEvent(
      loggingEvent.getLoggerName,
      loggingEvent.getLevel.toString,
      loggingEvent.getFormattedMessage,
      OffsetDateTime.ofInstant(new Date(loggingEvent.getTimeStamp).toInstant, ZoneId.of("UTC"))
    )
    ServerSentEvent(write(message)(SerializationFormat(OffsetDateTimeSerializer)), eventType)
  }
}
