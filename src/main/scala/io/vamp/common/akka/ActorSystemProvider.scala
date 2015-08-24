package io.vamp.common.akka

import akka.actor.{Actor, ActorSystem}

trait ActorSystemProvider {
  implicit def actorSystem: ActorSystem
}

trait ActorSystemProviderForActors extends ActorSystemProvider {
  this: Actor =>
  implicit def actorSystem: ActorSystem = context.system
}