package io.vamp.core.persistence

import akka.actor.Props
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.SchedulerActor.Tick
import io.vamp.common.akka._
import io.vamp.core.model.artifact.Artifact

import scala.collection.mutable
import scala.language.postfixOps

object CachePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[CachePersistenceActor], args: _*)
}

case class CachePersistenceInfo(`type`: String, artifacts: Map[String, Map[String, Any]])

class CachePersistenceActor(target: ActorDescription) extends DecoratorPersistenceActor(target) with ScheduleSupport {

  private val store: mutable.Map[String, mutable.Map[String, CacheEntry]] = new mutable.HashMap()

  override protected def respond(request: Any): Any = request match {
    case Tick => evict()
    case other => super.respond(other)
  }

  override protected def allowedRequestType(request: Any): Boolean = request == Tick || super.allowedRequestType(request)

  override protected def start() = {
    actorFor(target) ! Start
    schedule(timeout.duration, timeout.duration)
  }

  override protected def shutdown() = {
    unschedule()
    actorFor(target) ! Shutdown
  }

  override protected def infoMap() = Map("cache" -> true)

  override protected def all(`type`: Class[_ <: Artifact]) = super.all(`type`)

  override protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = super.all(`type`, page, perPage)

  override protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = super.create(artifact, source, ignoreIfExists)

  override protected def read(name: String, `type`: Class[_ <: Artifact]) = super.read(name, `type`)

  override protected def update(artifact: Artifact, source: Option[String], create: Boolean) = super.update(artifact, source, create)

  override protected def delete(name: String, `type`: Class[_ <: Artifact]) = super.delete(name, `type`)

  override protected def readExpanded(name: String, `type`: Class[_ <: Artifact]) = super.readExpanded(name, `type`)

  private def evict() = {
    log.debug(s"${getClass.getSimpleName}: evict")
  }
}

private case class CacheEntry(artifact: Artifact, evict: Boolean = false, deleted: Boolean = false)
