package io.magnetic.vamp_common.notification

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.language.postfixOps

trait Notification {
  
}

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)

@deprecated
object Notification extends DefaultPackageMessageResolverProvider with NotificationProvider with LoggingNotificationProvider

