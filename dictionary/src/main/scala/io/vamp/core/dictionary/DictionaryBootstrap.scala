package io.vamp.core.dictionary

import akka.actor.{ActorSystem, Props}
import io.vamp.common.akka.{Bootstrap, IoC}

object DictionaryBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    IoC.createActor(Props(classOf[DictionaryActor]))
  }
}
