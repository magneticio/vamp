package io.magnetic.vamp_common.notification

import akka.actor.{AbstractLoggingActor, Props}

object NotificationActor {
  def props: Props = Props[NotificationActor]
}

case class Error(notification: Notification, message: String)

case class Info(notification: Notification, message: String)

class NotificationActor extends AbstractLoggingActor {
  override def receive: Receive = {
    case Error(notification, message) => log.error(message)
    case Info(notification, message) => log.info(message)
  }
}
