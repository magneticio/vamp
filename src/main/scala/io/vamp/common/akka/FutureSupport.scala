package io.vamp.common.akka

import akka.util.Timeout
import io.vamp.common.notification.{NotificationProvider, Notification}

import scala.concurrent._
import scala.util.{Failure, Success}

trait FutureSupport {

  def offload(future: Future[Any])(implicit timeout: Timeout): Any = {
    Await.ready(future, timeout.duration)
    future.value.get match {
      case Success(result) => result
      case Failure(result) => result
    }
  }
}

trait FutureSupportNotification extends FutureSupport {
  this: NotificationProvider =>

  def offload(future: Future[Any], `class`: Class[_ <: Notification])(implicit timeout: Timeout): Any = {
    Await.ready(future, timeout.duration)
    future.value.get match {
      case Success(result) => result
      case Failure(failure) => exceptionNotification(failure, `class`)
    }
  }

  def exceptionNotification(failure: AnyRef, `class`: Class[_ <: Notification]) = reportException(`class`.getConstructors()(0).newInstance(failure).asInstanceOf[Notification])
}
