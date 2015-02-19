package io.magnetic.vamp_common.notification

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.language.postfixOps

trait Notification

case class NotificationErrorException(notification: Notification, message: String) extends RuntimeException(message)

object Notification extends DefaultPackageMessageResolverProvider {

  private val logger = Logger(LoggerFactory.getLogger(Notification.getClass))

  def info(notification: Notification) = logger.info(messageResolver.resolve(notification))

  def error(notification: Notification) = {
    val message = messageResolver.resolve(notification)
    logger.error(message)
    throw new NotificationErrorException(notification, message)
  }
}

