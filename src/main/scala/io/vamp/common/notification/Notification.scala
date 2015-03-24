package io.vamp.common.notification

trait Notification

trait ErrorNotification extends Notification {
  def reason: Any
}

trait PulseEvent {
  def tags: List[String] = Nil

  def schema: String = ""
}

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)
