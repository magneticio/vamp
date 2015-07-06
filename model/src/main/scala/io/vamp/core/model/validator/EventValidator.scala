package io.vamp.core.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.event.{Event, EventQuery}
import io.vamp.core.model.notification.{EventQueryTimeError, NoTagEventError}

trait EventValidator {
  this: NotificationProvider =>

  def validateEvent: (Event => Event) = { event =>
    if (event.tags.isEmpty) throwException(NoTagEventError)
    event
  }

  def validateEventQuery: (EventQuery => EventQuery) = { eventQuery =>
    eventQuery.timestamp.foreach { time =>
      if ((time.lt.isDefined && time.lte.isDefined) || (time.gt.isDefined && time.gte.isDefined)) throwException(EventQueryTimeError)
    }
    eventQuery
  }
}
