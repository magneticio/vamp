package io.vamp.gateway_driver.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedGatewayDriverRequest(request: Any) extends Notification with RequestError

case class GatewayResponseError(reason: Any) extends Notification with ErrorNotification
