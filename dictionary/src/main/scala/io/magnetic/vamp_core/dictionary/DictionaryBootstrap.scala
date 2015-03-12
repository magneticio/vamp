package io.magnetic.vamp_core.dictionary

import akka.actor.ActorSystem
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}

object DictionaryBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DictionaryActor)
  }
}
