package io.vamp.gateway_driver.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedGatewayDriverRequest(request: Any) extends Notification with RequestError

case class GatewayDriverResponseError(reason: Any) extends Notification with ErrorNotification

case class UnsupportedGatewayStoreRequest(request: Any) extends Notification with RequestError

case class GatewayStoreResponseError(reason: Any) extends Notification with ErrorNotification
