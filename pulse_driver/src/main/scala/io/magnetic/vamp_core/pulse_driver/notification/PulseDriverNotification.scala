package io.magnetic.vamp_core.container_driver.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.{ErrorNotification, Notification}

case class UnsupportedPulseDriverRequest(request: Any) extends Notification with RequestError

case class PulseResponseError(reason: Any) extends Notification with ErrorNotification
