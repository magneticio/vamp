package io.vamp.lifter.notification

import io.vamp.common.notification.Notification

case class PersistenceInitializationFailure(message: String) extends Notification
