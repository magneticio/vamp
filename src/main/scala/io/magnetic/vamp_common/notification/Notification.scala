package io.magnetic.vamp_common.notification

trait Notification

trait ErrorNotification extends Notification {
  def reason: Any
}

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)
