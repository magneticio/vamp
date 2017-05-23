package io.vamp.model.notification

import io.vamp.common.notification.Notification

object EmptyImportError extends Notification

object ImportDefinitionError extends Notification

case class ImportReferenceError(reference: String) extends Notification
