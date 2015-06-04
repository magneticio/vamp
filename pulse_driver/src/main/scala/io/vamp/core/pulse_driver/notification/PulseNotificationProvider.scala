package io.vamp.core.pulse_driver.notification

import java.time.OffsetDateTime

import io.vamp.common.akka.ActorSupport
import io.vamp.common.notification._
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.PulseDriverActor.Publish
import io.vamp.pulse.model.Event
import io.vamp.pulse.notification.PulseEvent

trait PulseNotificationProvider extends LoggingNotificationProvider {
  this: MessageResolverProvider with ActorSupport =>

  def tags: Set[String]

  override def info(notification: Notification): Unit = {
    actorFor(PulseDriverActor) ! Publish(eventOf(notification, Set("notification")))
    super.info(notification)
  }

  def eventOf(notification: Notification, globalTags: Set[String]): Event = notification match {
    case event: PulseEvent =>
      val allTags = globalTags ++ tags ++ event.tags

      event.`type` match {
        case Some(_) => Event(allTags, event.value, OffsetDateTime.now(), event.`type`.get)
        case None => Event(allTags, event.value, OffsetDateTime.now())
      }

    case _ => Event(globalTags ++ tags, notification, OffsetDateTime.now())
  }
}
