package io.vamp.common.notification

import akka.actor.{ AbstractLoggingActor, Actor, Props }

object LoggingNotificationActor {
  def props: Props = Props[LoggingNotificationActor]
}

case class Error(notification: Notification, message: String)

case class Info(notification: Notification, message: String)

trait NotificationActor {
  this: Actor ⇒
  override def receive: Receive = {
    case Error(notification, message) ⇒ error(notification, message)
    case Info(notification, message)  ⇒ info(notification, message)
  }

  def error(notification: Notification, message: String)

  def info(notification: Notification, message: String)
}

class LoggingNotificationActor extends AbstractLoggingActor with NotificationActor {
  override def error(notification: Notification, message: String): Unit = {
    log.error(message)
  }

  override def info(notification: Notification, message: String): Unit = {
    log.info(message)
  }
}
