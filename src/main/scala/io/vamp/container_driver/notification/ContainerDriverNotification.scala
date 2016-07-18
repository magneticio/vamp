package io.vamp.container_driver.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedContainerDriverError(name: String) extends Notification

case class UnsupportedContainerDriverRequest(request: Any) extends Notification with RequestError

case class ContainerResponseError(reason: Any) extends Notification with ErrorNotification

case class UnsupportedDeployableType(schema: String, supported: String) extends Notification

object UndefinedDockerImage extends Notification

object UndefinedMarathonApplication extends Notification