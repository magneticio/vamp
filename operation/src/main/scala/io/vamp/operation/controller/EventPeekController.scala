package io.vamp.operation.controller

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EventPeekController extends EventValue {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  def peek(tags: List[String], window: FiniteDuration): Future[Option[Double]] = last(tags.toSet, window).map {
    case Some(value) ⇒ Option(value.toString.toDouble)
    case _           ⇒ None
  }
}
