package io.vamp.persistence.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }

case class UnsupportedPersistenceRequest(request: Any) extends Notification with RequestError

case class ArtifactNotFound(name: String, `type`: Class[_]) extends Notification

case class PersistenceOperationFailure(reason: Any) extends Notification with ErrorNotification

case class ArtifactAlreadyExists(name: String, `type`: Class[_]) extends Notification

case class NotificationMessageNotRestored(message: String) extends Notification

case class UnsupportedParameterToPersist(name: String, parent: String, parameterType: String) extends Notification

case class CorruptedDataException() extends Exception with Notification

case class UnknownDataFormatException(kind: String) extends Exception with Notification

case class DataNotYetLoadedException() extends Exception with Notification
