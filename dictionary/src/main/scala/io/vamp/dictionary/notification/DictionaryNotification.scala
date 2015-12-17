package io.vamp.dictionary.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.Notification

case class UnsupportedDictionaryRequest(request: Any) extends Notification with RequestError

