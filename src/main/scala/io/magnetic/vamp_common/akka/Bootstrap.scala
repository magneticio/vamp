package io.magnetic.vamp_common.akka

import akka.actor.ActorSystem

trait Bootstrap {
  def run(implicit actorSystem: ActorSystem)
}