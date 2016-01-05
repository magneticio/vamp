package io.vamp.dictionary

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ Bootstrap, IoC }

object DictionaryBootstrap extends Bootstrap {
  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = IoC.createActor[DictionaryActor] :: Nil
}
