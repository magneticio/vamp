package io.vamp.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.event.{ Event, EventQuery }
import io.vamp.model.notification.{ EventQueryTimeError, EventTypeError, NoTagEventError }

trait EventValidator {
  this: NotificationProvider ⇒

  protected val typeMatcher = """^[\w\d\-_]+$""".r

  def validateEvent: (Event ⇒ Event) = { event ⇒
    if (event.tags.isEmpty) throwException(NoTagEventError)

    event.`type` match {
      case typeMatcher(_*) ⇒
      case _               ⇒ throwException(EventTypeError(event.`type`))
    }

    event
  }

  def validateEventQuery: (EventQuery ⇒ EventQuery) = { eventQuery ⇒
    eventQuery.timestamp.foreach { time ⇒
      if ((time.lt.isDefined && time.lte.isDefined) || (time.gt.isDefined && time.gte.isDefined)) throwException(EventQueryTimeError)
    }
    eventQuery
  }
}
