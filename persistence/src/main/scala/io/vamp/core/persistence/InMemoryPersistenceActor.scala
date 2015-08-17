package io.vamp.core.persistence

import io.vamp.common.akka._
import akka.actor.Props
import akka.event.LoggingAdapter
import io.vamp.common.http.OffsetEnvelope
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import io.vamp.core.persistence.notification.{ArtifactAlreadyExists, ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import org.json4s.native.Serialization._

import scala.collection.mutable
import scala.language.postfixOps

object InMemoryPersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[InMemoryPersistenceActor], args: _*)
}

class InMemoryPersistenceActor extends PersistenceActor with TypeOfArtifact {

  implicit val formats = CoreSerializationFormat.default

  private val store = new InMemoryStore(log)

  protected def info() = store.info()

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    log.debug(s"${getClass.getSimpleName}: all [${`type`.getSimpleName}] of $page per $perPage")
    store.all(`type`, page, perPage)
  }

  protected def create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = false): Artifact = {
    log.debug(s"${getClass.getSimpleName}: create [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    store.create(artifact, source, ignoreIfExists)
  }

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
    log.debug(s"${getClass.getSimpleName}: read [${`type`.getSimpleName}] - $name}")
    store.read(name, `type`)
  }

  protected def update(artifact: Artifact, source: Option[String] = None, create: Boolean = false): Artifact = {
    log.debug(s"${getClass.getSimpleName}: update [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    store.update(artifact, source, create)
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Artifact = {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    store.delete(name, `type`)
  }
}

class InMemoryStore(log: LoggingAdapter) extends TypeOfArtifact with PersistenceNotificationProvider {

  private val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

  def info() = Map[String, Any](
    "type" -> "in-memory [no persistence]",
    "artifacts" -> (store.map {
      case (key, value) => key -> Map[String, Any]("count" -> value.values.size)
    } toMap))

  def all(`type`: Class[_ <: Artifact]): List[Artifact] = {
    store.get(typeOf(`type`)) match {
      case None => Nil
      case Some(map) => map.values.toList
    }
  }

  def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    val artifacts = all(`type`)
    val total = artifacts.size
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)

    ArtifactResponseEnvelope(artifacts.slice((p - 1) * pp, p * pp), total, rp, rpp)
  }

  def create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = false): Artifact = {
    artifact match {
      case blueprint: DefaultBlueprint => blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).foreach(breed => create(breed, ignoreIfExists = true))
      case _ =>
    }

    val branch = typeOf(artifact.getClass)
    store.get(branch) match {
      case None =>
        val map = new mutable.HashMap[String, Artifact]()
        map.put(artifact.name, artifact)
        store.put(branch, map)
      case Some(map) => map.get(artifact.name) match {
        case None => map.put(artifact.name, artifact)
        case Some(_) =>
          if (!ignoreIfExists) throwException(ArtifactAlreadyExists(artifact.name, artifact.getClass))
          map.put(artifact.name, artifact)
      }
    }
    artifact
  }

  def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = store.get(typeOf(`type`)) match {
    case None => None
    case Some(map) => map.get(name)
  }

  def update(artifact: Artifact, source: Option[String] = None, create: Boolean = false): Artifact = {
    store.get(typeOf(artifact.getClass)) match {
      case None => if (create) this.create(artifact) else throwException(ArtifactNotFound(artifact.name, artifact.getClass))
      case Some(map) =>
        if (map.get(artifact.name).isEmpty)
          if (create) this.create(artifact) else throwException(ArtifactNotFound(artifact.name, artifact.getClass))
        else
          map.put(artifact.name, artifact)
    }
    artifact
  }

  def delete(name: String, `type`: Class[_ <: Artifact]): Artifact = store.get(typeOf(`type`)) match {
    case None => throwException(ArtifactNotFound(name, `type`))
    case Some(map) =>
      if (map.get(name).isEmpty)
        throwException(ArtifactNotFound(name, `type`))
      else
        map.remove(name).get
  }
}

trait TypeOfArtifact {
  this: NotificationProvider =>

  def typeOf(`type`: Class[_]): String = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) => "deployments"
    case t if classOf[Breed].isAssignableFrom(t) => "breeds"
    case t if classOf[Blueprint].isAssignableFrom(t) => "blueprints"
    case t if classOf[Sla].isAssignableFrom(t) => "slas"
    case t if classOf[Scale].isAssignableFrom(t) => "scales"
    case t if classOf[Escalation].isAssignableFrom(t) => "escalations"
    case t if classOf[Routing].isAssignableFrom(t) => "routings"
    case t if classOf[Filter].isAssignableFrom(t) => "filters"
    case t if classOf[Workflow].isAssignableFrom(t) => "workflows"
    case t if classOf[ScheduledWorkflow].isAssignableFrom(t) => "scheduled-workflows"
    case _ => throwException(UnsupportedPersistenceRequest(`type`))
  }
}
