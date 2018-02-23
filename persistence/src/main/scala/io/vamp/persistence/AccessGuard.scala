package io.vamp.persistence

import io.vamp.common.notification.NotificationProvider
import io.vamp.persistence.notification.DataNotYetLoadedException

object AccessGuard {

  object LoadAll

}

trait AccessGuard {
  this: NotificationProvider ⇒

  private var loaded = false

  protected var validData = true

  protected def guard(): Unit = if (!loaded) throwException(DataNotYetLoadedException())

  protected def removeGuard(): Unit = if (!loaded) loaded = true
}
