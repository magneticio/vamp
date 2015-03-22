package io.vamp.core.persistence

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.persistence.actor.PersistenceActor

object PersistenceBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PersistenceActor)
  }
}
