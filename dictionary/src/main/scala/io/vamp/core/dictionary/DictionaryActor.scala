package io.vamp.core.dictionary


import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.core.dictionary.DictionaryActor.{DictionaryMessage, Get}
import io.vamp.core.dictionary.notification.{DictionaryNotificationProvider, NoAvailablePortError, UnsupportedDictionaryRequest}
import io.vamp.core.model.artifact.DefaultScale

import scala.concurrent.duration._

object DictionaryActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.dictionary.response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[DictionaryActor])

  trait DictionaryMessage

  case class Get(key: String) extends DictionaryMessage

  val portAssignment = "vamp://routes/port?deployment=%s&port=%d"

  val hostResolver = "vamp://routes/host"

  val containerScale = "vamp://container/scale?deployment=%s&cluster=%s&service=%s"
}

case class DictionaryEntry(key: String, value: String)

class DictionaryActor extends CommonReplyActor with DictionaryNotificationProvider {

  implicit val timeout = DictionaryActor.timeout

  private val portAssignment = toRegExp(DictionaryActor.portAssignment)
  private val portRange = ConfigFactory.load().getString("vamp.core.dictionary.port-range").split("-").map(_.toInt)
  private var currentPort = portRange(0) - 1
  private val hostResolver = toRegExp(DictionaryActor.hostResolver)
  private val containerScale = toRegExp(DictionaryActor.containerScale)

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

    case hostResolver(_*) =>
      ConfigFactory.load().getString("vamp.core.router-driver.host")

    case containerScale(deployment, cluster, service) =>
      val config = ConfigFactory.load()
      val cpu = config.getDouble("vamp.core.dictionary.default-scale.cpu")
      val memory = config.getDouble("vamp.core.dictionary.default-scale.memory")
      val instances = config.getInt("vamp.core.dictionary.default-scale.instances")
      DefaultScale("", cpu, memory, instances)

    case value => value
  }

}
