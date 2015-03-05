package io.magnetic.vamp_core.persistence.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.Notification

import scala.language.existentials

case class UnsupportedPersistenceRequest(request: Any) extends Notification with RequestError

case class ArtifactNotFound(name: String, `type`: Class[_]) extends Notification

case class PersistenceOperationFailure(exception: Any) extends Notification

case class ArtifactAlreadyExists(name: String, `type`: Class[_]) extends Notification


