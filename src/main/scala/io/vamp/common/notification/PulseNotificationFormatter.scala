package io.vamp.common.notification

import java.time.OffsetDateTime
import io.vamp.common.pulse.api.Event

/**
 * Created by lazycoder on 13/03/15.
 */
trait PulseNotificationEventFormatter {
  def formatNotification(notification: Notification, tags: List[String] = List.empty): Event
}

trait DefaultNotificationEventFormatter extends PulseNotificationEventFormatter with TagResolverProvider {
  override def formatNotification(notification: Notification, tags: List[String]): Event = {
    Event(tags ++ resolveTags(notification), notification, OffsetDateTime.now(), notification.getClass.getCanonicalName)
  }
}