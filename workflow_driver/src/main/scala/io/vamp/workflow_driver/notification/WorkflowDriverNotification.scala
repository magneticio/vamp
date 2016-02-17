package io.vamp.workflow_driver.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedWorkflowDriverError(name: String) extends Notification

case class UnsupportedWorkflowDriverRequest(request: Any) extends Notification with RequestError

case class WorkflowResponseError(reason: Any) extends Notification with ErrorNotification
