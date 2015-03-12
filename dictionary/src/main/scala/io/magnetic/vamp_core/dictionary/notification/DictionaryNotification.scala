package io.magnetic.vamp_core.dictionary.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.{ErrorNotification, Notification}

case class UnsupportedDictionaryRequest(request: Any) extends Notification with RequestError

case class NoAvailablePortError(begin: Int, end: Int) extends Notification
