package io.vamp.common.notification

trait Notification

trait ErrorNotification extends Notification {
  def reason: Any
}

case class GenericErrorNotification(reason: Any) extends ErrorNotification

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)
