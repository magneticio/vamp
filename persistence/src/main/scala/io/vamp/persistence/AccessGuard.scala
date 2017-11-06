package io.vamp.persistence

import io.vamp.common.notification.NotificationProvider
import io.vamp.persistence.notification.DataNotYetLoadedException

trait AccessGuard {
  this: NotificationProvider ⇒

  private var loaded = false

  protected def guard(): Unit = if (!loaded) throwException(DataNotYetLoadedException())

  protected def removeGuard(): Unit = if (!loaded) loaded = true
}
