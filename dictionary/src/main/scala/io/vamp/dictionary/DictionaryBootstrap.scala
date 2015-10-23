package io.vamp.dictionary

import akka.actor.ActorSystem
import io.vamp.common.akka.{ Bootstrap, IoC }

object DictionaryBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor[DictionaryActor]
  }
}
