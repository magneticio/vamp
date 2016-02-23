package io.vamp.persistence.db

import akka.event.LoggingAdapter
import io.vamp.common.http.OffsetEnvelope
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.model.workflow.{ ScheduledWorkflow, Workflow }
import io.vamp.persistence.notification.{ PersistenceNotificationProvider, UnsupportedPersistenceRequest }
import io.vamp.persistence.operation._
import org.json4s.native.Serialization._

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.{ implicitConversions, postfixOps }

class InMemoryPersistenceActor extends PersistenceActor with TypeOfArtifact {

  implicit val formats = CoreSerializationFormat.default

  private val store = new InMemoryStore(log)

  protected def info() = Future {
    store.info()
  }

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope] = Future.successful {
    log.debug(s"${getClass.getSimpleName}: all [${`type`.getSimpleName}] of $page per $perPage")
    store.all(`type`, page, perPage)
  }

  protected def get(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]] = Future.successful {
    log.debug(s"${getClass.getSimpleName}: read [${`type`.getSimpleName}] - $name}")
    store.read(name, `type`)
  }

  protected def set(artifact: Artifact): Future[Artifact] = Future.successful {
    log.debug(s"${getClass.getSimpleName}: set [${artifact.getClass.getSimpleName}] - ${write(artifact)}")
    store.set(artifact)
  }

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Future[Boolean] = Future.successful {
    log.debug(s"${getClass.getSimpleName}: delete [${`type`.getSimpleName}] - $name}")
    store.delete(name, `type`).isDefined
  }
}

class InMemoryStore(log: LoggingAdapter) extends TypeOfArtifact with PersistenceNotificationProvider {

  private val store: mutable.Map[String, mutable.Map[String, Artifact]] = new mutable.HashMap()

  def info() = Map[String, Any](
    "type" -> "in-memory [no persistence]",
    "artifacts" -> (store.map {
      case (key, value) ⇒ key -> Map[String, Any]("count" -> value.values.size)
    } toMap))

  def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope = {
    val artifacts = store.get(`type`) match {
      case None      ⇒ Nil
      case Some(map) ⇒ map.values.toList
    }
    val total = artifacts.size
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, ArtifactResponseEnvelope.maxPerPage)
    val (rp, rpp) = OffsetEnvelope.normalize(total, p, pp, ArtifactResponseEnvelope.maxPerPage)

    ArtifactResponseEnvelope(artifacts.slice((p - 1) * pp, p * pp), total, rp, rpp)
  }

  def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = store.get(`type`).flatMap(_.get(name))

  def set(artifact: Artifact): Artifact = {
    store.get(artifact.getClass) match {
      case None      ⇒ create(artifact)
      case Some(map) ⇒ map.put(artifact.name, artifact)
    }
    artifact
  }

  def delete(name: String, `type`: Class[_ <: Artifact]): Option[Artifact] = {
    store.get(`type`) flatMap {
      case map ⇒
        val artifact = map.remove(name)
        if (artifact.isEmpty) log.debug(s"Artifact not found for deletion: ${type2string(`type`)}:$name")
        artifact
    }
  }

  private def create(artifact: Artifact): Artifact = {
    store.get(artifact.getClass) match {
      case None ⇒
        val map = new mutable.HashMap[String, Artifact]()
        map.put(artifact.name, artifact)
        store.put(artifact.getClass, map)
      case Some(map) ⇒ map.get(artifact.name) match {
        case None    ⇒ map.put(artifact.name, artifact)
        case Some(_) ⇒ map.put(artifact.name, artifact)
      }
    }
    artifact
  }
}

trait TypeOfArtifact {
  this: NotificationProvider ⇒

  implicit def type2string(`type`: Class[_]): String = `type` match {
    // gateway persistence
    case t if classOf[RouteTargets].isAssignableFrom(t) ⇒ "route-targets"
    case t if classOf[GatewayPort].isAssignableFrom(t) ⇒ "gateway-ports"
    case t if classOf[GatewayDeploymentStatus].isAssignableFrom(t) ⇒ "gateway-deployment-statuses"
    // deployment persistence
    case t if classOf[DeploymentServiceState].isAssignableFrom(t) ⇒ "deployment-service-states"
    case t if classOf[DeploymentServiceInstances].isAssignableFrom(t) ⇒ "deployment-service-instances"
    case t if classOf[DeploymentServiceEnvironmentVariables].isAssignableFrom(t) ⇒ "deployment-service-environment-variables"
    //
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ "gateways"
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ "deployments"
    case t if classOf[Breed].isAssignableFrom(t) ⇒ "breeds"
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ "blueprints"
    case t if classOf[Sla].isAssignableFrom(t) ⇒ "slas"
    case t if classOf[Scale].isAssignableFrom(t) ⇒ "scales"
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ "escalations"
    case t if classOf[Route].isAssignableFrom(t) ⇒ "routes"
    case t if classOf[Filter].isAssignableFrom(t) ⇒ "filters"
    case t if classOf[Rewrite].isAssignableFrom(t) ⇒ "rewrites"
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ "workflows"
    case t if classOf[ScheduledWorkflow].isAssignableFrom(t) ⇒ "scheduled-workflows"
    case _ ⇒ throwException(UnsupportedPersistenceRequest(`type`))
  }
}
