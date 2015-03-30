package io.vamp.core.persistence.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.Notification

import scala.language.existentials

case class UnsupportedPersistenceRequest(request: Any) extends Notification with RequestError

case class ArtifactNotFound(name: String, `type`: Class[_]) extends Notification

case class PersistenceOperationFailure(exception: Any) extends Notification

case class ArtifactAlreadyExists(name: String, `type`: Class[_]) extends Notification

case class NotificationMessageNotRestored(message: String) extends Notification

case class UnsupportedParameterToPersist(name : String, parent : String, parameterType : String ) extends Notification