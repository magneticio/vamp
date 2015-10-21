package io.vamp.common.akka

import akka.actor.{ Actor, ActorRefFactory }

import scala.concurrent.ExecutionContext

trait ExecutionContextProvider {
  implicit def executionContext: ExecutionContext
}

trait ActorRefFactoryExecutionContextProvider extends ExecutionContextProvider {
  implicit def executionContext: ExecutionContext = actorRefFactory.dispatcher

  implicit def actorRefFactory: ActorRefFactory
}

trait ActorExecutionContextProvider extends ExecutionContextProvider {
  this: Actor ⇒
  implicit def executionContext: ExecutionContext = context.dispatcher
}
