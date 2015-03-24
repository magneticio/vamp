package io.vamp.common.notification

trait Notification

trait ErrorNotification extends Notification {
  def reason: Any
}

trait PulseEvent {
  def tags: List[String] = Nil

  def schema: String = ""

  def value: AnyRef = this
}

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)
