package io.vamp.common.akka

import akka.actor.Actor
import akka.actor.Status.Failure
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.notification.{Notification, NotificationProvider}
import io.vamp.common.vitals.InfoRequest

import scala.runtime.BoxedUnit

trait RequestError extends Notification {
  def request: Any
}

trait ReplyActor {
  this: Actor with NotificationProvider =>

  final override def receive: Receive = {
    case request if allowedRequestType(request) => reply(request) match {
      case response: BoxedUnit =>
      case response => sender ! response
    }
    case request => sender ! unsupported(request)
  }

  protected def allowedRequestType(request: Any) = {
    requestType.isAssignableFrom(request.getClass) || (request == InfoRequest) || (request == Start) || (request == Shutdown)
  }

  protected def requestType: Class[_]

  protected def reply(request: Any): Any

  protected def errorRequest(request: Any): RequestError

  protected def unsupported(request: Any) = Failure(reportException(errorRequest(request)))

}

trait CommonReplyActor extends CommonSupportForActors with ReplyActor with FutureSupportNotification