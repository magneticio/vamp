package io.vamp.common.akka

import akka.actor.ActorContext

trait ActorContextProvider {
  implicit def context: ActorContext
}
