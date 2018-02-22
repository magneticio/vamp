package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.Namespace

import scala.concurrent.Future
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.util.Try

trait EventPeekController extends AbstractController with EventValue {

  def peek(tags: List[String], window: FiniteDuration)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = last(tags.toSet, window).map {
    case Some(value) ⇒ Option(value.toString.toDouble)
    case _           ⇒ None
  }

  def windowFrom(seconds: Option[String], defaultValue: ⇒ FiniteDuration): FiniteDuration = {
    seconds.flatMap(s ⇒ Try(s.toInt).toOption).map(s ⇒ if (s < 0) defaultValue else s.seconds).getOrElse(defaultValue)
  }
}
