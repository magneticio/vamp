package io.magnetic.vamp_core.rest_api.notification

import io.magnetic.vamp_common.notification.Notification

case class UnexpectedEndOfRequest() extends Notification
case class UnexpectedArtifact(artifact: String) extends Notification
case class InconsistentArtifactName(parameter: String, body: String) extends Notification

