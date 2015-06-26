package io.vamp.core.pulse.notification

import akka.actor.AbstractLoggingActor
import io.vamp.common.akka.ActorExecutionContextProvider
import io.vamp.common.notification._
import io.vamp.core.pulse.elasticsearch.ElasticsearchClientProvider


abstract class AbstractPulseNotificationActor(override protected val elasticsearchUrl: String) extends AbstractLoggingActor with NotificationActor with TagResolverProvider with ElasticsearchClientProvider with PulseNotificationEventFormatter with ActorExecutionContextProvider {

  override def error(notification: Notification, message: String): Unit = {
    elasticsearchClient.sendEvent(formatNotification(notification, List("notification", "error")))
  }

  override def info(notification: Notification, message: String): Unit = {
    elasticsearchClient.sendEvent(formatNotification(notification, List("notification", "info")))
  }
}
