package io.vamp.router_driver.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedRouterDriverRequest(request: Any) extends Notification with RequestError

case class RouterResponseError(reason: Any) extends Notification with ErrorNotification
