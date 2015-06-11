package io.vamp.common.akka

import akka.actor.Actor
import akka.actor.Status.Failure
import io.vamp.common.notification.{Notification, NotificationProvider}
import io.vamp.common.vitals.InfoRequest

import scala.runtime.BoxedUnit

trait RequestError extends Notification {
  def request: Any
}

trait ReplyActor {
  this: Actor with NotificationProvider =>

  final override def receive: Receive = {
    case request if requestType.isAssignableFrom(request.getClass) || (request == InfoRequest) => reply(request) match {
      case response: BoxedUnit =>
      case response => sender ! response
    }
    case request => sender ! unsupported(request)
  }

  protected def requestType: Class[_]

  protected def reply(request: Any): Any

  protected def errorRequest(request: Any): RequestError

  protected def unsupported(request: Any) = Failure(exception(errorRequest(request)))

}

trait CommonReplyActor extends CommonSupportForActors with ReplyActor with FutureSupportNotification