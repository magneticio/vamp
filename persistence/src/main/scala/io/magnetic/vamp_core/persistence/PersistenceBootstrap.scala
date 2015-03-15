package io.magnetic.vamp_core.persistence

import akka.actor.ActorSystem
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.persistence.actor.PersistenceActor

object PersistenceBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PersistenceActor)
  }
}
