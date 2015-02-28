package io.magnetic.vamp_common.akka

import akka.actor.Actor
import akka.actor.Status.Failure
import io.magnetic.vamp_common.notification.{Notification, NotificationProvider}

trait RequestError extends Notification {
  def request: Any
}

trait ReplyActor {
  this: Actor with NotificationProvider =>

  final override def receive: Receive = {
    case request if request.getClass == requestType => reply(request) match {
      case response if response.getClass != classOf[Unit] => sender ! response
      case _ => unsupported(request)
    }
    case request => sender ! unsupported(request)
  }

  protected def requestType: Class[_]

  protected def reply: Receive

  protected def errorRequest(request: Any): RequestError

  protected def unsupported(request: Any) = Failure(exception(errorRequest(request)))

}
