package io.magnetic.vamp_common.notification

trait Notification

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)
