package io.vamp.common.akka

import akka.actor.{ActorContext, ActorRef, PoisonPill}
import io.vamp.common.akka.Bootstrap._

trait Bootstrap {
  def run(implicit context: ActorContext)

  def shutdown(implicit context: ActorContext) = {}
}

object Bootstrap {

  object Start extends Serializable

  object Shutdown extends Serializable

}

trait ActorBootstrap extends Bootstrap {

  def actors: List[ActorRef]

  def run(implicit context: ActorContext) = actors.foreach(actor => actor ! Start)

  override def shutdown(implicit context: ActorContext) = {
    actors.reverse.foreach { actor =>
      actor ! Shutdown
      actor ! PoisonPill
    }
  }
}
