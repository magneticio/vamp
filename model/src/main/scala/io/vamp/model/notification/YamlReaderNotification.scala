package io.vamp.model.notification

import io.vamp.common.notification.Notification

case class YamlParsingError(message: String, exception: Exception) extends Notification

case class MissingPathValueError(path: String) extends Notification

case class UnexpectedTypeError(path: String, expected: Class[_], actual: Class[_]) extends Notification

case class UnexpectedInnerElementError(path: String, found: Class[_]) extends Notification

case class EitherReferenceOrAnonymous(name: String, reference: String) extends Notification

case class NotAnonymousError(name: String) extends Notification

case class UnexpectedElement(element: Map[String, _], message: String) extends Notification

case class IllegalName(name: String) extends Notification

case class IllegalStrictName(name: String) extends Notification

case class InconsistentArtifactName(given: String, found: String) extends Notification

case class InconsistentArtifactKind(given: String, found: String) extends Notification
