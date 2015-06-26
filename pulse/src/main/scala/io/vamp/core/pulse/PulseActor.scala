package io.vamp.core.pulse

import akka.actor.Props
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.pulse.notification.{PulseDriverNotificationProvider, PulseResponseError, UnsupportedPulseDriverRequest}

object PulseActor extends ActorDescription {

  def props(args: Any*): Props = Props(classOf[PulseActor], args: _*)

  trait PulseDriverMessage

}

class PulseActor extends CommonReplyActor with PulseDriverNotificationProvider {

  import PulseActor._

  override protected def requestType: Class[_] = classOf[PulseDriverMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseDriverRequest(request)

  def reply(request: Any) = try {
    request match {

      case InfoRequest =>

      case _ => unsupported(request)
    }
  } catch {
    case e: Throwable => exception(PulseResponseError(e))
  }
}

