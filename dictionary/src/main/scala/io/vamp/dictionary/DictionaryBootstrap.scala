package io.vamp.dictionary

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }

object DictionaryBootstrap extends ActorBootstrap {
  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = IoC.createActor[DictionaryActor] :: Nil
}
