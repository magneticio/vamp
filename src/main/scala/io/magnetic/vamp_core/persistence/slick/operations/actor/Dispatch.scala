package io.magnetic.vamp_core.persistence.slick.operations.actor

import io.magnetic.vamp_core.persistence.common.operations.message.Messages
import Messages.BreedOps
import io.magnetic.vamp_core.persistence.common.operations.message.Messages.DispatchError
import io.magnetic.vamp_core.persistence.slick.model.Schema
import akka.actor.{Props, Actor}


class Dispatch(val schema: Schema) extends Actor{
  val breedActor = context.actorOf(BreedActor.props(schema))
  
  override def receive: Receive = {
    case BreedOps(msg) => breedActor.forward(msg)
    // TODO add operations for blueprint
    // TODO add operations for deployments
    // TODO add operations for slas
    // TODO add operations for scales
    // TODO add operations for escalations
    // TODO add operations for routings
    // TODO add operations for filters

    case _ => sender() ! DispatchError
  }
}

object Dispatch {
  def props(schema: Schema): Props = Props(new Dispatch(schema))
  
}
