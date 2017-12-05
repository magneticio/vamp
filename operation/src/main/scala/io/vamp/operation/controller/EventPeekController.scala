package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.Namespace

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait EventPeekController extends AbstractController with EventValue {

  def peek(tags: List[String], window: FiniteDuration)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] =
    for {
      last <- lastDoubleValue(tags.toSet, window)
    } yield {
      last.flatMap{ value =>
        Try(Some(value.toString.asInstanceOf[Double])).getOrElse(None)
      }
    }
}
