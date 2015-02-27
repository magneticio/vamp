package io.magnetic.vamp_core.operation

import akka.actor.Actor
import akka.actor.Status.Failure
import io.magnetic.vamp_core.operation.notification.{OperationNotificationProvider, UnsupportedOperationRequest}

trait OperationRequest

trait OperationActor {
  this: Actor with OperationNotificationProvider =>

  final override def receive: Receive = {
    case request: OperationRequest => reply(request) match {
      case response if response.getClass != classOf[Unit] => sender ! response
      case _ => unsupported(request)
    }
    case request => sender ! unsupported(request)
  }

  protected def reply: Receive

  protected def unsupported(request: Any) = Failure(exception(UnsupportedOperationRequest(request)))
}
