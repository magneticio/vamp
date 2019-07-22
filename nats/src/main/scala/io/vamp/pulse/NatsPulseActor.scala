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

    case Publish(event, publishEventValue) ⇒ reply((validateEvent andThen publish(publishEventValue) andThen broadcast(publishEventValue))(Event.expandTags(event)), classOf[EventIndexError])

    case Query(envelope) ⇒ IoC.actorFor[PulseActorSupport].forward(Query(envelope))

    case GetPercolator(name) ⇒ IoC.actorFor[PulseActorSupport].forward(GetPercolator(name))

    case RegisterPercolator(name, tags, kind, message) ⇒ IoC.actorFor[PulseActorSupport].forward(RegisterPercolator(name, tags, kind, message))

    case UnregisterPercolator(name) ⇒ IoC.actorFor[PulseActorSupport].forward(UnregisterPercolator(name))

    case any ⇒ unsupported(UnsupportedPulseRequest(any))
  }

  private def publish(publishEventValue: Boolean)(event: Event): Future[Any] = Future {
    logger.info("Publish event to publishers")
    // send it to NATS publisher
    Try(IoC.actorFor[PulseActorPublisher] ! Publish(event, publishEventValue)).recover {
      case e: Throwable ⇒ logger.error("Error sending messages to Nats Publisher Actor ", e)
    }
    // Send it to Elasticsearch
    Try(IoC.actorFor[PulseActorSupport] ! Publish(event, publishEventValue)).recover {
      case e: Throwable ⇒ logger.error("Error sending messages to Elasticsearch Actor ", e)
    }

    // this is needed for percolator
    // this is with a random id, normally elastic search id was used but now it doesn't exist.
    // refactor this if it becomes a problem
    val randomId = Random.alphanumeric.take(10).mkString("")
    event.copy(id = Option(randomId))
  }

  private def broadcast(publishEventValue: Boolean): Future[Any] ⇒ Future[Any] = _.map {
    case event: Event ⇒ percolate(publishEventValue)(event)
    case other        ⇒ other
  }
}