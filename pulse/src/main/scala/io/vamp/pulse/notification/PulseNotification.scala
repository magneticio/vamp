package io.vamp.pulse.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedPulseRequest(request: Any) extends Notification with RequestError

case class PulseResponseError(reason: Any) extends Notification with ErrorNotification

object ElasticsearchInitializationTimeoutError extends Notification

case class EventIndexError(reason: Any) extends Notification with ErrorNotification

case class EventQueryError(reason: Any) extends Notification with ErrorNotification

