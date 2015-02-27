package io.magnetic.vamp_common.notification

import akka.actor.Actor
import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory


trait NotificationProvider {
  def info(notification: Notification)

  def exception(notification: Notification): Exception

  def error(notification: Notification) = throw exception(notification)
}

trait LoggingNotificationProvider extends NotificationProvider {
  this: MessageResolverProvider =>

  private val logger = Logger(LoggerFactory.getLogger(classOf[Notification]))

  def info(notification: Notification) = logger.info(messageResolver.resolve(notification))

  def exception(notification: Notification): Exception = {
    val message = messageResolver.resolve(notification)
    logger.error(message)
    NotificationErrorException(notification, message)
  }
}

trait ActorNotificationProvider extends NotificationProvider {
  this: Actor with MessageResolverProvider =>

  private val notificationActor = context.actorOf(NotificationActor.props)

  def info(notification: Notification) = {
    notificationActor ! Info(notification, messageResolver.resolve(notification))
  }

  def exception(notification: Notification): Exception = {
    val message = messageResolver.resolve(notification)
    notificationActor ! Error(notification, message)
    NotificationErrorException(notification, message)
  }
}


