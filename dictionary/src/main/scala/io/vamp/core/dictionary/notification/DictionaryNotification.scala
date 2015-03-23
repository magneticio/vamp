package io.vamp.core.dictionary.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ErrorNotification, Notification}

case class UnsupportedDictionaryRequest(request: Any) extends Notification with RequestError

case class NoAvailablePortError(begin: Int, end: Int) extends Notification
