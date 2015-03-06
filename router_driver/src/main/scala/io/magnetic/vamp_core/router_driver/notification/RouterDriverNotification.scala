package io.magnetic.vamp_core.router_driver.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.Notification

case class UnsupportedRouterDriverRequest(request: Any) extends Notification with RequestError

case class RouterResponseError(any: Any) extends Notification
