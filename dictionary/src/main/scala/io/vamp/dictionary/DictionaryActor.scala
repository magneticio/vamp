package io.vamp.dictionary

import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.dictionary.notification.{ DictionaryNotificationProvider, UnsupportedDictionaryRequest }

object DictionaryActor {
  lazy val timeout = Config.timeout("vamp.dictionary.response-timeout")
}

case class DictionaryEntry(key: String, value: String)

class DictionaryActor extends CommonSupportForActors with DictionaryNotificationProvider {

  implicit val timeout = DictionaryActor.timeout

  def receive = {
    case any ⇒ unsupported(UnsupportedDictionaryRequest(any))
  }
}
