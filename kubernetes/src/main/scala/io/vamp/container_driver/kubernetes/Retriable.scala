package io.vamp.container_driver.kubernetes

import akka.actor.Scheduler
import akka.pattern.after
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.FiniteDuration

trait Retriable {

  def retryIndefinitely[T](op: ⇒ T, delay: FiniteDuration)(implicit ec: ExecutionContext, s: Scheduler): Future[T] =
    Future(op) recoverWith { case _ ⇒ after(delay, s)(retryIndefinitely(op, delay)) }

  def retryWithLimit[T](op: ⇒ T, delay: FiniteDuration, iteration: Int, limit: Int)(implicit ec: ExecutionContext, s: Scheduler): Future[T] =
    Future(op) recoverWith {
      case _ ⇒
        if (iteration < limit)
          after(delay * iteration, s)(retryWithLimit(op, delay, iteration + 1, limit))
        else
          Future.failed(new Exception("Give up on retrial"))
    }

}