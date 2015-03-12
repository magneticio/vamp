package io.magnetic.vamp_core.dictionary

import java.net.URL

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.dictionary.DictionaryActor.{DictionaryMessage, Get}
import io.magnetic.vamp_core.dictionary.notification.{DictionaryNotificationProvider, UnsupportedDictionaryRequest}

import scala.concurrent.duration._

object DictionaryActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.dictionary.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[DictionaryActor])

  trait DictionaryMessage

  case class Get(key: String) extends DictionaryMessage

}

class DictionaryActor extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with DictionaryNotificationProvider {

  implicit val timeout = DictionaryActor.timeout

  override protected def requestType: Class[_] = classOf[DictionaryMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDictionaryRequest(request)

  def reply(request: Any) = try {
    request match {
      case Get(key) => 33000
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => e
  }
}
