package io.vamp.pulse

import akka.actor.{ Actor, ActorLogging, ActorRef }
import io.vamp.model.event.Event

import scala.collection.mutable

object Percolator {

  case class RegisterPercolator(name: String, tags: Set[String], message: Any)

  case class UnregisterPercolator(name: String)

}

trait Percolator {
  this: Actor with ActorLogging ⇒

  case class PercolatorEntry(tags: Set[String], actor: ActorRef, message: Any)

  protected val percolators = mutable.Map[String, PercolatorEntry]()

  def registerPercolator(name: String, tags: Set[String], message: Any) = {
    log.info(s"Registering percolator '$name' for tags '${tags.mkString(", ")}'.")
    percolators.put(name, PercolatorEntry(tags, sender(), message))
  }

  def unregisterPercolator(name: String) = {
    if (percolators.remove(name).nonEmpty)
      log.info(s"Percolator successfully removed for '$name'.")
  }

  def percolate: (Event ⇒ Event) = { (event: Event) ⇒
    percolators.foreach {
      case (name, percolator) ⇒
        if (percolator.tags.forall(event.tags.contains)) {
          log.debug(s"Percolate match for '$name'.")
          percolator.actor ! (percolator.message -> event)
        }
    }
    event
  }
}
