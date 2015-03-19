package io.vamp.common.akka

import akka.actor.ActorSystem

trait Bootstrap {
  def run(implicit actorSystem: ActorSystem)
}