package io.vamp.core.persistence

import akka.actor.Props
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka.SchedulerActor.Tick
import io.vamp.common.akka._
import io.vamp.core.model.artifact.Artifact

import scala.language.postfixOps

object CachePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[CachePersistenceActor], args: _*)
}

class CachePersistenceActor(target: ActorDescription) extends DecoratorPersistenceActor(target) with TypeOfArtifact with ScheduleSupport {

  private var store: Map[String, Map[String, CacheEntry]] = Map()

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

  override protected def infoMap() = Map[String, Any](
    "cache" -> store.map {
      case (key, value) => key -> Map[String, Any]("count" -> value.values.size)
    })

  override protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) = addToCache(super.create(artifact, source, ignoreIfExists))

  override protected def update(artifact: Artifact, source: Option[String], create: Boolean) = addToCache(super.update(artifact, source, create))

  override protected def delete(name: String, `type`: Class[_ <: Artifact]) = deleteFromCache(super.delete(name, `type`))

  override protected def all(`type`: Class[_ <: Artifact]) = {
    super.all(`type`).map(addToCache)
    store.get(typeOf(`type`)) match {
      case None => Nil
      case Some(map) => map.values.flatMap(_.artifact).toList
    }
  }

  override protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int) = {
    // ignores the cache
    val envelope = super.all(`type`, page, perPage)
    envelope.copy(response = envelope.response.map(addToCache))
  }

  override protected def read(name: String, `type`: Class[_ <: Artifact]) = {
    val group = typeOf(`type`)
    store.get(group) match {
      case Some(map) if map.get(name).nonEmpty => map.get(name).flatMap(entry => entry.artifact)
      case _ =>
        super.read(name, `type`) match {
          case Some(artifact) => Some(addToCache(artifact))
          case None =>
            updateCache(group, name, CacheEntry(None))
            None
        }
    }
  }

  private def addToCache(artifact: Artifact): Artifact = {
    val group = typeOf(artifact.getClass)
    log.debug(s"${getClass.getSimpleName} add to cache: $group://${artifact.name}")
    updateCache(group, artifact.name, CacheEntry(Some(artifact)))
    artifact
  }

  private def deleteFromCache(artifact: Artifact): Artifact = {
    val group = typeOf(artifact.getClass)
    log.debug(s"${getClass.getSimpleName} mark as deleted: $group://${artifact.name}")
    updateCache(group, artifact.name, CacheEntry(None))
    artifact
  }

  private def updateCache(group: String, name: String, entry: CacheEntry) = store.get(group) match {
    case None =>
      store = store + (group -> Map(name -> entry))
    case Some(map) =>
      store = store + (group -> (map ++ Map(name -> entry)))
  }

  private def evict() = {
    log.debug(s"${getClass.getSimpleName}: evict store.")
    store = store.map { case (group, artifacts) =>
      group -> artifacts.filterNot({
        case (name, entry) =>
          if (entry.evict) log.debug(s"${getClass.getSimpleName} evicted: $group://$name.")
          entry.evict
      }).map {
        case (name, entry) => name -> entry.copy(evict = true)
      }
    }
  }
}

private case class CacheEntry(artifact: Option[Artifact], evict: Boolean = false)
