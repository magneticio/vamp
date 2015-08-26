package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }
import io.vamp.common.akka.Bootstrap._

trait Bootstrap {
  def run(implicit actorSystem: ActorSystem)

  def shutdown(implicit actorSystem: ActorSystem) = {}
}

object Bootstrap {

  object Start extends Serializable

  object Shutdown extends Serializable

}

trait ActorBootstrap extends Bootstrap {

  def actors: List[ActorRef]

  def run(implicit actorSystem: ActorSystem) = actors.foreach(actor ⇒ actor ! Start)

  override def shutdown(implicit actorSystem: ActorSystem) = {
    actors.reverse.foreach { actor ⇒
      actor ! Shutdown
      actor ! PoisonPill
    }
  }
}
