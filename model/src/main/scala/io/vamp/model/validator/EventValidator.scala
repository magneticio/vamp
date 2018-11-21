package io.vamp.model.validator

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.event.{ Event, EventQuery }
import io.vamp.model.notification.{ EventQueryTimeError, EventTypeError, NoTagEventError }

trait EventValidator extends LazyLogging {
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
      if ((time.lt.isDefined && time.lte.isDefined) || (time.gt.isDefined && time.gte.isDefined)) {
        logger.info(
          "Event query validation failed for time range ( lt: {}, lte: {}, gt: {}, gte {} )",
          time.lt.getOrElse(""), time.lte.getOrElse(""), time.gt.getOrElse(""), time.gte.getOrElse(""))
        throwException(EventQueryTimeError)
      }
    }
    eventQuery
  }
}
