package io.magnetic.vamp_core.operation

import akka.actor.Actor
import io.magnetic.vamp_common.akka.{ReplyActor, RequestError}
import io.magnetic.vamp_core.operation.notification.{OperationNotificationProvider, UnsupportedOperationRequest}

trait OperationRequest

trait OperationActor extends ReplyActor {
  this: Actor with OperationNotificationProvider =>

  override protected def requestType: Class[_] = classOf[OperationRequest]

  override protected def errorRequest(request: Any): RequestError = UnsupportedOperationRequest(request)
}
