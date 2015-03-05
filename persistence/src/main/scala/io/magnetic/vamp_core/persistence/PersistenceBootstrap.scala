package io.magnetic.vamp_core.persistence

import akka.actor.ActorSystem
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}

object PersistenceBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(PersistenceActor)
  }
}
