package io.vamp.common.akka

import akka.util.Timeout

import scala.concurrent._
import scala.util.{Failure, Success}


trait FutureSupport {

  @deprecated("Blocking", "0.7.10")
  def offload(future: Future[Any])(implicit timeout: Timeout): Any = {
    Await.ready(future, timeout.duration)
    future.value.get match {
      case Success(result) => result
      case Failure(result) => result
    }
  }
}
