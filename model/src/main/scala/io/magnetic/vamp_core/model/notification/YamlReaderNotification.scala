package io.magnetic.vamp_core.model.notification

import io.magnetic.vamp_common.notification.Notification

import scala.language.existentials

case class YamlParsingError(message: String, exception: Exception) extends Notification

case class MissingPathValueError(path: String) extends Notification

case class UnexpectedTypeError(path: String, expected: Class[_], actual: Class[_]) extends Notification

case class UnexpectedInnerElementError(path: String, found: Class[_]) extends Notification

case class EitherReferenceOrAnonymous(name: String, reference: String) extends Notification