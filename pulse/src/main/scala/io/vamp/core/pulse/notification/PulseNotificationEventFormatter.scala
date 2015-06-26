package io.vamp.core.pulse.notification

import java.time.OffsetDateTime

import io.vamp.common.notification._
import io.vamp.core.pulse.event.Event

trait PulseNotificationEventFormatter {
  def formatNotification(notification: Notification, tags: List[String] = List.empty): Event
}

trait DefaultNotificationEventFormatter extends PulseNotificationEventFormatter with TagResolverProvider {
  override def formatNotification(notification: Notification, tags: List[String]): Event = {
    Event((tags ++ resolveTags(notification)).toSet, notification, OffsetDateTime.now(), notification.getClass.getCanonicalName)
  }
}
