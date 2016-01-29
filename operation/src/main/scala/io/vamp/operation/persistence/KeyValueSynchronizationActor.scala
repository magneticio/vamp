package io.vamp.operation.persistence

import java.net.URLEncoder

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.model.artifact.{ Artifact, Deployment, Gateway }
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.notification.{ InternalServerError, OperationNotificationProvider }
import io.vamp.operation.persistence.KeyValueSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor
import org.json4s.native.Serialization._

import scala.language.postfixOps
import scala.util.{ Failure, Success }

class KeyValueSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[KeyValueSynchronizationActor] ! SynchronizeAll
}

object KeyValueSynchronizationActor {

  sealed trait KeyValueMessages

  object SynchronizeAll extends KeyValueMessages

  case class SynchronizeGateways(gateways: List[Gateway], stored: Map[String, String]) extends KeyValueMessages

  case class SynchronizeDeployments(deployments: List[Deployment], stored: Map[String, String]) extends KeyValueMessages

}

class KeyValueSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import KeyValueSynchronizationActor._

  private val gatewaysPath = "gateways" :: Nil

  private val deploymentsPath = "deployments" :: Nil

  private implicit val timeout = PersistenceActor.timeout

  def receive = {
    case SynchronizeAll ⇒ synchronize()
    case SynchronizeGateways(gateways, stored) ⇒ synchronizeGateways(gateways, stored)
    case SynchronizeDeployments(deployments, stored) ⇒ synchronizeDeployments(deployments, stored)
    case _ ⇒
  }

  private def synchronize() = {
    val sendTo = self

    (for {
      gateways ← allArtifacts[Gateway]
      source ← checked[Map[String, String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.All(gatewaysPath))
    } yield (gateways, source)) onComplete {
      case Success((gateways, source)) ⇒ sendTo ! SynchronizeGateways(gateways, source)
      case Failure(error)              ⇒ reportException(InternalServerError(error))
    }

    (for {
      deployments ← allArtifacts[Deployment]
      source ← checked[Map[String, String]](IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.All(deploymentsPath))
    } yield (deployments, source)) onComplete {
      case Success((deployments, source)) ⇒ sendTo ! SynchronizeDeployments(deployments, source)
      case Failure(error)                 ⇒ throwException(InternalServerError(error))
    }
  }

  private def synchronizeGateways(gateways: List[Gateway], stored: Map[String, String]) = {
    gateways foreach { gateway ⇒

      val json = serialize(gateway)

      val persist = stored.get(gateway.name) match {
        case Some(value) ⇒ json != value
        case None        ⇒ true
      }

      if (persist) IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(gatewaysPath :+ URLEncoder.encode(gateway.name, "UTF-8"), Option(json))
    }

    stored foreach {
      case (name, _) ⇒ if (!gateways.exists(_.name == name)) IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(gatewaysPath :+ URLEncoder.encode(name, "UTF-8"), None)
    }
  }

  private def synchronizeDeployments(deployments: List[Deployment], stored: Map[String, String]) = {
    deployments.foreach { deployment ⇒

      val json = serialize(deployment)

      val persist = stored.get(deployment.name) match {
        case Some(value) ⇒ json != value
        case None        ⇒ true
      }

      if (persist) IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(deploymentsPath :+ URLEncoder.encode(deployment.name, "UTF-8"), Option(json))
    }

    stored foreach {
      case (name, _) ⇒ if (!deployments.exists(_.name == name)) IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(deploymentsPath :+ URLEncoder.encode(name, "UTF-8"), None)
    }
  }

  private def serialize(artifact: Artifact) = write(artifact)(CoreSerializationFormat.full)
}
