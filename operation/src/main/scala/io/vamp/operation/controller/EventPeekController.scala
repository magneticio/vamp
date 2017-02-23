package io.vamp.operation.controller

import io.vamp.common.akka.CommonProvider

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait EventPeekController extends EventValue {
  this: CommonProvider ⇒

  def peek(tags: List[String], window: FiniteDuration): Future[Option[Double]] = last(tags.toSet, window).map {
    case Some(value) ⇒ Option(value.toString.toDouble)
    case _           ⇒ None
  }
}
