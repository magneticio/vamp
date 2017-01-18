package io.vamp.pulse

import akka.actor.{ Actor, ActorLogging, ActorRef }
import io.vamp.model.event.Event

import scala.collection.mutable

object Percolator {

  sealed trait PercolatorMessage

  case class GetPercolator(name: String) extends PercolatorMessage

  case class RegisterPercolator(name: String, tags: Set[String], `type`: Option[String], message: Any) extends PercolatorMessage

  case class UnregisterPercolator(name: String) extends PercolatorMessage

}

trait Percolator {
  this: Actor with ActorLogging ⇒

  case class PercolatorEntry(tags: Set[String], `type`: Option[String], actor: ActorRef, message: Any)

  protected val percolators = mutable.Map[String, PercolatorEntry]()

  def getPercolator(name: String) = percolators.get(name)

  def registerPercolator(name: String, tags: Set[String], `type`: Option[String], message: Any) = {
    percolators.put(name, PercolatorEntry(tags, `type`, sender(), message)) match {
      case Some(entry) if entry.tags == tags && entry.`type` == `type` ⇒
      case _ ⇒ log.info(s"Percolator '$name' has been registered for tags '${tags.mkString(", ")}'.")
    }
  }

  def unregisterPercolator(name: String) = {
    if (percolators.remove(name).nonEmpty)
      log.info(s"Percolator successfully removed for '$name'.")
  }

  def percolate(publishEventValue: Boolean): (Event ⇒ Event) = { (event: Event) ⇒
    percolators.foreach {
      case (name, percolator) ⇒
        if (percolator.tags.forall(event.tags.contains) && (percolator.`type`.isEmpty || percolator.`type`.get == event.`type`)) {
          log.debug(s"Percolate match for '$name'.")
          val send = if (publishEventValue) event else event.copy(value = None)
          percolator.actor ! (percolator.message → send)
        }
    }
    event
  }
}
