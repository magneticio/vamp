package io.magnetic.vamp_core.rest_api.util

import akka.actor._

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait ExecutionContextProvider {
  implicit def executionContext: ExecutionContext
}

trait ActorRefFactoryExecutionContextProvider extends ExecutionContextProvider with ActorRefFactoryProvider {
  implicit def executionContext: ExecutionContext = actorRefFactory.dispatcher
}

trait ActorExecutionContextProvider extends ExecutionContextProvider {
  this: Actor ⇒
  implicit def executionContext: ExecutionContext = context.dispatcher
}
