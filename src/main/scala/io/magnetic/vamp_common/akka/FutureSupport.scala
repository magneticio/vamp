package io.magnetic.vamp_common.akka

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

trait FutureSupport {

  def offLoad(future: Future[Any], atMost: Duration): Any = {
    Await.ready(future, atMost)
    future.value.get match {
      case Success(result) => result
      case Failure(result) => result
    }
  }

  def sequentialExecution(futures: Seq[Future[Any]])(implicit ec: ExecutionContext): Future[List[Any]] = {
    futures.foldLeft(Future(List.empty[Any])) {
      (previousFuture, next) =>
        for {
          previousResults <- previousFuture
          next <- next
        } yield previousResults :+ next
    }
  }
}
