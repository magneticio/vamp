package io.magnetic.vamp_common.notification

import akka.actor.{Actor, ActorRef}
import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.pulse.PulseClientProvider
import io.magnetic.vamp_common.pulse.api.Event
import org.slf4j.LoggerFactory

trait NotificationProvider {
  def message(notification: Notification): String

  def info(notification: Notification)

  def exception(notification: Notification): Exception

  def error(notification: Notification) = throw exception(notification)
}

trait LoggingNotificationProvider extends NotificationProvider {
  this: MessageResolverProvider =>

  private val logger = Logger(LoggerFactory.getLogger(classOf[Notification]))

  def message(notification: Notification) = messageResolver.resolve(notification)

  def info(notification: Notification) = logger.info(message(notification))

  def exception(notification: Notification): Exception = {
    val msg = message(notification)
    logger.error(msg)

    notification match {
      case error: ErrorNotification => error.reason match {
        case reason: Throwable => logger.error(reason.getMessage, reason)
        case reason => logger.error(reason.toString)
      }
      case _ =>
    }

    NotificationErrorException(notification, msg)
  }
}

trait PulseLoggingNotificationProvider extends LoggingNotificationProvider with TagResolverProvider with PulseClientProvider {
  this: MessageResolverProvider =>

  override def info(notification: Notification): Unit = {
    client.sendEvent(
      Event(resolveTags(notification,List("info", "notification" )),
        Map("object" -> Map( notification.getClass.getCanonicalName -> notification))
      )
    )
    super.info(notification)
  }

  override def exception(notification: Notification): Exception = {
    client.sendEvent(
      Event(resolveTags(notification, List("error", "notification")),
        Map("object" -> Map( notification.getClass.getCanonicalName -> notification))
      )
    )
    super.exception(notification)
  }
}

trait ActorNotificationProvider extends NotificationProvider {
  this: Actor with MessageResolverProvider =>

  protected val notificationActor: ActorRef

  def message(notification: Notification) = messageResolver.resolve(notification)

  def info(notification: Notification) = {
    notificationActor ! Info(notification, message(notification))
  }

  def exception(notification: Notification): Exception = {
    val msg = message(notification)
    notificationActor ! Error(notification, msg)
    NotificationErrorException(notification, msg)
  }
}



