package io.vamp.pulse

import akka.actor.Actor
import io.vamp.common.ClassMapper
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.model.event._
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.Percolator.{ GetPercolator, RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.notification._

import scala.concurrent.Future
import io.vamp.common.akka.IoC

import scala.util.{ Random, Try }

class NatsPulseActorMapper extends ClassMapper {
  val name = "nats"
  val clazz: Class[_] = classOf[NatsPulseActor]
}

object NatsPulseActor {

  val config: String = PulseActor.config

}

/**
 * NATS Pulse Actor forward messages to NATS, Elasticsearch and also forwards other types of messages to Elasticsearch
 *
 */
class NatsPulseActor extends NamespaceValueResolver with PulseActor {

  import PulseActor._



  def receive: Actor.Receive = {

    case InfoRequest ⇒ IoC.actorFor[PulseActorSupport].forward(InfoRequest)

    case StatsRequest ⇒ IoC.actorFor[PulseActorSupport].forward(StatsRequest)

    case Publish(event, publishEventValue) ⇒
      IoC.actorFor[PulseActorSupport].forward(Publish(event, publishEventValue))
      IoC.actorFor[PulseActorPublisher].forward(Publish(event, publishEventValue))

    case Query(envelope) ⇒ IoC.actorFor[PulseActorSupport].forward(Query(envelope))

    case GetPercolator(name) ⇒ IoC.actorFor[PulseActorSupport].forward(GetPercolator(name))

    case RegisterPercolator(name, tags, kind, message) ⇒ IoC.actorFor[PulseActorSupport].forward(RegisterPercolator(name, tags, kind, message))

    case UnregisterPercolator(name) ⇒ IoC.actorFor[PulseActorSupport].forward(UnregisterPercolator(name))

    case any ⇒ unsupported(UnsupportedPulseRequest(any))
  }
}