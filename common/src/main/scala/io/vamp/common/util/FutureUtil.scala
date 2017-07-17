package io.vamp.common.util

import scala.concurrent.{ExecutionContext, Future}

object FutureUtil {

  /**
    * Iterative equivalent to Future.sequence
    * Future.sequence executes the futures inside a list in parallel. This version executes them iteratively.
    * This, for example is usefull when you don't want to flood something with requests but have them executed sequentially
    * @param futureSeq  A list of Future generators. The futures created by those generators will be executed in sequence.
    *                   We must use generators because if we'd use actual Future objects, their execution will have already
    *                   been triggered upon Future initialization
    * @return A future containing the sequence of all results. If any of the futures fails, the first Future.failed becomes the result returned
    */

  def IterativeFuture[T](futureSeq: List[() => Future[T]])(implicit executionContext: ExecutionContext): Future[Seq[T]] =
    futureExtraction(futureSeq)(Nil)


  private def futureExtraction[T](values: List[() => Future[T]])(soFar: List[T])(implicit executionContext: ExecutionContext): Future[List[T]] =
    values match {
      case Nil => Future.successful(Nil)
      case finalValue :: Nil => finalValue().map(x => (x :: soFar).reverse)
      case intermediaryValue :: restOfValues => intermediaryValue().flatMap(x => futureExtraction(restOfValues)(x :: soFar))
    }
}
