package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem }
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{ Level, LoggerContext, Logger ⇒ LogbackLogger }
import ch.qos.logback.core.AppenderBase
import io.vamp.common.Namespace
import org.slf4j.{ Logger, LoggerFactory }

import scala.collection.mutable

object LogPublisherHub {

  private val logger = LoggerFactory.getLogger(LogPublisherHub.getClass)

  private val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
  private val rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME)

  private val sessions: mutable.Map[String, LogPublisher] = new mutable.HashMap()

  def subscribe(to: ActorRef, level: String, loggerName: Option[String], encoder: (ILoggingEvent) ⇒ AnyRef)(implicit actorSystem: ActorSystem, namespace: Namespace): Unit = {
    val appenderLevel = Level.toLevel(level, Level.INFO)
    val appenderLogger = loggerName.map(context.getLogger).getOrElse(rootLogger)

    val exists = sessions.get(to.toString).exists { publisher ⇒
      publisher.level == appenderLevel && publisher.logger.getName == appenderLogger.getName
    }

    if (!exists) {
      unsubscribe(to)
      if (appenderLevel != Level.OFF) {
        logger.info(s"Starting log publisher [${appenderLevel.levelStr}] '${appenderLogger.getName}': $to")
        val publisher = LogPublisher(to, appenderLogger, appenderLevel, encoder)
        publisher.start()
        sessions.put(to.toString, publisher)
      }
    }
  }

  def unsubscribe(to: ActorRef): Unit = {
    sessions.remove(to.toString).foreach { publisher ⇒
      logger.info(s"Stopping log publisher: $to")
      publisher.stop()
    }
  }
}

private case class LogPublisher(to: ActorRef, logger: LogbackLogger, level: Level, encoder: (ILoggingEvent) ⇒ AnyRef)(implicit actorSystem: ActorSystem, namespace: Namespace) {

  private val filter = new ThresholdFilter()
  filter.setLevel(level.levelStr)

  private val appender = new AppenderBase[ILoggingEvent] {
    override def append(loggingEvent: ILoggingEvent) = to ! encoder(loggingEvent)
  }

  appender.addFilter(filter)
  appender.setName(to.toString)

  def start() = {
    val context = logger.getLoggerContext
    filter.setContext(context)
    appender.setContext(context)
    filter.start()
    appender.start()
    logger.addAppender(appender)
  }

  def stop() = {
    appender.stop()
    filter.stop()
    logger.detachAppender(appender)
  }
}
