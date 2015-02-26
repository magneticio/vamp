package io.magnetic.vamp_core.operation.notification

import io.magnetic.vamp_common.notification.Notification

case class InconsistentResourceName(parameter: String, body: String) extends Notification


