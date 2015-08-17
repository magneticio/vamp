package io.vamp.common.akka

import akka.actor.Actor
import akka.actor.Status.Failure
import akka.pattern.pipe
import io.vamp.common.notification.{Notification, NotificationProvider}

import scala.concurrent.Future

trait RequestError extends Notification {
  def request: Any
}

trait ReplyActor {
  this: Actor with ExecutionContextProvider with NotificationProvider =>

  protected def reply[A](future: Future[A]): Unit =
    pipe(future.recover { case failure => asFailure(failure) }) to sender()

  protected def asFailure(request: Any) =
    Failure(reportException(asRequestError(request)))

  protected def asRequestError(request: Any): RequestError
}

trait CommonReplyActor extends CommonSupportForActors with ReplyActor