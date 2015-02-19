package io.magnetic.vamp_common.notification

import akka.actor.Actor
import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.notification.Notification._
import org.slf4j.LoggerFactory


trait NotificationProvider {
  def info(notification: Notification)

  def error(notification: Notification)
}


trait LoggingNotificationProvider extends NotificationProvider {
  private val logger = Logger(LoggerFactory.getLogger(Notification.getClass))

  def info(notification: Notification) = logger.info(messageResolver.resolve(notification))

  def error(notification: Notification) = {
    val message = messageResolver.resolve(notification)
    logger.error(message)
    throw new NotificationErrorException(notification, message)
  }
}

trait ActorNotificationProvider extends NotificationProvider {
  this: Actor =>
  private val notificationActor = context.actorOf(NotificationActor.props)
  

  def info(notification: Notification) = {
    notificationActor ! Info(notification, messageResolver.resolve(notification))
  }

  def error(notification: Notification) = {
    val message = messageResolver.resolve(notification)
    notificationActor ! Error(notification, message)
    throw new NotificationErrorException(notification, message)
  }
    
  
}


