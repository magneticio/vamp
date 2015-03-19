package io.vamp.common.akka

import akka.actor.Actor
import akka.actor.Status.Failure
import io.vamp.common.notification.{Notification, NotificationProvider}

trait RequestError extends Notification {
  def request: Any
}

trait ReplyActor {
  this: Actor with NotificationProvider =>

  final override def receive: Receive = {
    case request if requestType.isAssignableFrom(request.getClass) => sender ! reply(request)
    case request => sender ! unsupported(request)
  }

  protected def requestType: Class[_]

  protected def reply(request: Any): Any

  protected def errorRequest(request: Any): RequestError

  protected def unsupported(request: Any) = Failure(exception(errorRequest(request)))

}
