package io.vamp.core.dictionary

import akka.actor.ActorSystem
import io.vamp.common.akka.{ActorSupport, Bootstrap}

object DictionaryBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DictionaryActor)
  }
}
