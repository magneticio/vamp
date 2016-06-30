package io.vamp.common.akka

import akka.actor.{ ActorRef, ActorSystem, PoisonPill }

trait Bootstrap {

  def run(): Unit = {}

  def shutdown(): Unit = {}
}

trait ActorBootstrap {

  private var actors: List[ActorRef] = Nil

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef]

  def run(implicit actorSystem: ActorSystem): Unit = {
    actors = createActors(actorSystem)
  }

  def shutdown(implicit actorSystem: ActorSystem): Unit = actors.reverse.foreach {
    _ ! PoisonPill
  }
}
