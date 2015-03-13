package io.magnetic.vamp_core.pulse_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.container_driver.notification.{PulseDriverNotificationProvider, PulseResponseError, UnsupportedPulseDriverRequest}
import io.magnetic.vamp_core.model.artifact.{Deployment, DeploymentCluster}
import io.magnetic.vamp_core.pulse_driver.PulseDriverActor.{LastSlaEvent, PulseDriverMessage}

import scala.concurrent.duration._

object PulseDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.pulse.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[PulseDriverActor], args: _*)

  trait PulseDriverMessage

  case class LastSlaEvent(deployment: Deployment, cluster: DeploymentCluster) extends PulseDriverMessage

}

class PulseDriverActor(driver: PulseDriver) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with PulseDriverNotificationProvider {

  implicit val timeout = PulseDriverActor.timeout

  override protected def requestType: Class[_] = classOf[PulseDriverMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case LastSlaEvent(deployment, cluster) => offLoad(driver.lastSlaEvent(deployment, cluster), classOf[PulseResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => exception(PulseResponseError(e))
  }
}
