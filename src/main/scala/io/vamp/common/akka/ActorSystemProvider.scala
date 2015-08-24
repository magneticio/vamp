package io.vamp.common.akka

import akka.actor.ActorSystem

trait ActorSystemProvider {
  implicit def actorSystem: ActorSystem
}
