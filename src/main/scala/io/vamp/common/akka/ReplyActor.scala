package io.vamp.common.akka

import _root_.io.vamp.common.notification.{ErrorNotification, Notification, NotificationProvider}
import akka.actor.Actor
import akka.pattern.pipe

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect._
import scala.util.{Failure, Success, Try}

trait RequestError extends ErrorNotification {
  def request: Any

  def reason = request
}

case class GenericErrorNotification(reason: Any) extends ErrorNotification


trait ReplyActor {
  this: Actor with ExecutionContextProvider with NotificationProvider =>

  def reply[T](magnet: ReplyMagnet[T], `class`: Class[_ <: Notification] = errorNotificationClass): Try[Future[T]] = {
    magnet.get.transform({ case future =>
      pipe {
        future.recover { case f => failure(f, `class`) }
      } to sender()
      Success(future)
    }, { case f => Failure(failure(f, `class`)) })
  }

  def checked[T <: Any : ClassTag](future: Future[_], `class`: Class[_ <: Notification] = errorNotificationClass): Future[T] = future map {
    case result if classTag[T].runtimeClass == result.getClass => result.asInstanceOf[T]
    case any => throw failure(any, `class`)
  }

  def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception =
    reportException(`class`.getConstructors()(0).newInstance(failure.asInstanceOf[AnyRef]).asInstanceOf[Notification])

  def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[GenericErrorNotification]

  def unsupported(requestError: RequestError) = reply(Future(reportException(requestError)))
}

sealed abstract class ReplyMagnet[+T] {
  def get: Try[Future[T]]
}

object ReplyMagnet {
  implicit def apply[T](any: => Future[T]): ReplyMagnet[T] = new ReplyMagnet[T] {
    override def get = Try(any)
  }
}
