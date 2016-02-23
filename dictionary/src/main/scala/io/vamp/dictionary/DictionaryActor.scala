package io.vamp.dictionary

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.dictionary.notification.{ DictionaryNotificationProvider, UnsupportedDictionaryRequest }

import scala.concurrent.duration._

object DictionaryActor {
  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.dictionary.response-timeout").seconds)
}

case class DictionaryEntry(key: String, value: String)

class DictionaryActor extends CommonSupportForActors with DictionaryNotificationProvider {

  implicit val timeout = DictionaryActor.timeout

  def receive = {
    case any ⇒ unsupported(UnsupportedDictionaryRequest(any))
  }
}
