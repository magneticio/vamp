package io.magnetic.vamp_core.rest_api.notification

import io.vamp.common.notification.Notification
import io.magnetic.vamp_core.model.artifact.Artifact

case class UnexpectedEndOfRequest() extends Notification
case class UnexpectedArtifact(artifact: String) extends Notification
case class InconsistentArtifactName(parameter: String, artifact: Artifact) extends Notification
case class UnsupportedRoutingWeightChangeError(weight: Int) extends Notification

