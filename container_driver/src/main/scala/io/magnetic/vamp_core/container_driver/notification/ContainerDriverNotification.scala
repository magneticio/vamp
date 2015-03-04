package io.magnetic.vamp_core.container_driver.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.Notification

case class UnsupportedContainerDriverRequest(request: Any) extends Notification with RequestError
