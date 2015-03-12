package io.magnetic.vamp_core.dictionary

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.dictionary.DictionaryActor.{DictionaryMessage, Get}
import io.magnetic.vamp_core.dictionary.notification.{DictionaryNotificationProvider, NoAvailablePortError, UnsupportedDictionaryRequest}

import scala.concurrent.duration._

object DictionaryActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.dictionary.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[DictionaryActor])

  trait DictionaryMessage

  case class Get(key: String) extends DictionaryMessage

  def portAssignment = "vamp://routes/port?deployment=%s&port=%d"

  def hostResolver = "vamp://routes/host"
}

case class DictionaryEntry(key: String, value: String)

class DictionaryActor extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with DictionaryNotificationProvider {

  implicit val timeout = DictionaryActor.timeout

  private val portAssignment = toRegExp(DictionaryActor.portAssignment)
  private val portRange = ConfigFactory.load().getString("deployment.dictionary.port.range").split("-").map(_.toInt)
  private var currentPort = portRange(0) - 1
  private val hostResolver = toRegExp(DictionaryActor.hostResolver)

  private def toRegExp(string: String) = {
    val value = string.
      replaceAllLiterally("/", "\\/").
      replaceAllLiterally("?", "\\?").
      replaceAllLiterally("%s", "(.*?)").
      replaceAllLiterally("%d", "(\\d*?)")
    s"^$value$$".r
  }
  override protected def requestType: Class[_] = classOf[DictionaryMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDictionaryRequest(request)

  def reply(request: Any) = try {
    request match {
      case Get(key) => get(key)
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => e
  }

  private def get(key: String) = key match {
    case portAssignment(deployment, port) =>
      if (currentPort == portRange(1))
        exception(NoAvailablePortError(portRange(0), portRange(1)))
      else {
        currentPort += 1
        currentPort
      }
    case hostResolver(_*) => ConfigFactory.load().getString("deployment.router.host")
    case value => value
  }

}
