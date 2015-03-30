package io.vamp.core.pulse_driver.notification

import java.time.OffsetDateTime

import io.vamp.common.akka.ActorSupport
import io.vamp.common.notification._
import io.vamp.pulse.api.Event
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.PulseDriverActor.Publish

trait PulseNotificationProvider extends LoggingNotificationProvider {
  this: MessageResolverProvider with ActorSupport =>

  def tags: List[String]

  override def info(notification: Notification): Unit = {
    actorFor(PulseDriverActor) ! Publish(eventOf(notification, List("notification", "info")))
    super.info(notification)
  }

  def eventOf(notification: Notification, globalTags: List[String]): Event = notification match {
    case event: PulseEvent => Event(globalTags ++ tags ++ event.tags, event.value, OffsetDateTime.now(), event.schema)
    case _ => Event(globalTags ++ tags, notification, OffsetDateTime.now(), "")
  }
}
