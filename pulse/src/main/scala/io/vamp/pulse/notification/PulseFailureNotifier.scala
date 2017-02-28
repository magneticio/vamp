package io.vamp.pulse.notification

import akka.actor.Actor
import io.vamp.common.NamespaceProvider
import io.vamp.common.akka.{ CommonActorLogging, IoC }
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.common.util.TextUtil
import io.vamp.model.event.Event
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish

trait PulseFailureNotifier {
  this: Actor with CommonActorLogging with NamespaceProvider ⇒

  def errorNotificationClass: Class[_ <: ErrorNotification]

  def reportException(notification: Notification): Exception

  def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = {
    publishFailureNotification(failure)
    reportException(`class`.getConstructors()(0).newInstance(failure.asInstanceOf[AnyRef]).asInstanceOf[Notification])
  }

  protected def failureNotificationEvent(failure: Any): Event = {
    val event = Event(Set("info", s"$typeName${Event.tagDelimiter}ERROR"), failure match {
      case e: Exception ⇒ if (e.getCause != null) e.getCause.getClass.getSimpleName else e.getClass.getSimpleName
      case _            ⇒ ""
    })
    log.debug(s"Pulse failure notification event: ${event.tags}")
    event
  }

  protected def publishFailureNotification(failure: Any): Unit = {
    implicit val actorSystem = context.system
    IoC.actorFor[PulseActor].tell(Publish(failureNotificationEvent(failure)), Actor.noSender)
  }

  def typeName = TextUtil.toSnakeCase(getClass.getSimpleName.replaceAll("Actor$", ""), dash = false)
}
