package io.vamp.common.akka

import akka.actor.Actor
import akka.pattern.pipe
import io.vamp.common.notification._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.reflect._
import scala.util.{ Failure, Success, Try }

trait RequestError extends ErrorNotification {
  def request: Any

  def reason = request
}

trait ReplyActor extends ReplyCheck {
  this: Actor with ExecutionContextProvider with NotificationProvider ⇒

  def reply[T](magnet: ReplyMagnet[T], `class`: Class[_ <: Notification] = errorNotificationClass): Try[Future[T]] = {
    magnet.get.transform({
      future ⇒
        pipe {
          future andThen {
            case Success(s)                             ⇒ s
            case Failure(n: NotificationErrorException) ⇒ n
            case Failure(f)                             ⇒ failure(f)
          }
        } to sender()
        Success(future)
    }, {
      case n: NotificationErrorException ⇒ Failure(n)
      case f                             ⇒ Failure(failure(f, `class`))
    })
  }

  def unsupported(requestError: RequestError) = reply(Future.failed(reportException(requestError)))
}

trait ReplyCheck {
  this: ExecutionContextProvider with NotificationProvider ⇒

  def checked[T <: Any: ClassTag](future: Future[_], `class`: Class[_ <: Notification] = errorNotificationClass): Future[T] = future map {
    case result if classTag[T].runtimeClass.isAssignableFrom(result.getClass) ⇒ result.asInstanceOf[T]
    case any ⇒ throw failure(any, `class`)
  }

  def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception =
    reportException(`class`.getConstructors()(0).newInstance(failure.asInstanceOf[AnyRef]).asInstanceOf[Notification])

  def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[GenericErrorNotification]
}

sealed abstract class ReplyMagnet[+T] {
  def get: Try[Future[T]]
}

object ReplyMagnet {
  implicit def apply[T](any: ⇒ Future[T]): ReplyMagnet[T] = new ReplyMagnet[T] {
    override def get = Try(any)
  }
}
