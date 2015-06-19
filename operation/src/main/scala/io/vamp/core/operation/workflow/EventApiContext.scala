package io.vamp.core.operation.workflow

import io.vamp.common.akka.FutureSupport

import scala.concurrent.ExecutionContext

class EventApiContext(ec: ExecutionContext) extends FutureSupport {

  implicit val executionContext = ec
}
