package io.vamp.operation.persistence

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.model.artifact.{ Deployment, Gateway }
import io.vamp.operation.notification.{ InternalServerError, OperationNotificationProvider }
import io.vamp.operation.persistence.KeyValueSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class KeyValueSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[KeyValueSynchronizationActor] ! SynchronizeAll
}

object KeyValueSynchronizationActor {

  sealed trait KeyValueMessages

  object SynchronizeAll extends KeyValueMessages

  case class SynchronizeDeployments(deployments: List[Deployment], stored: List[String]) extends KeyValueMessages

  case class SynchronizeGateways(gateways: List[Gateway], stored: List[String]) extends KeyValueMessages

}

class KeyValueSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import KeyValueSynchronizationActor._

  def receive = {
    case SynchronizeAll ⇒ synchronize()
    //case Synchronize(deployments, gateways) ⇒ synchronize(deployments, gateways)
    case _              ⇒
  }

  private def synchronize() = {
    val sendTo = self
    implicit val timeout = PersistenceActor.timeout

    (for {
      deployments ← allArtifacts[Deployment]
      source ← Future.successful(List.empty[String]) //IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.All(path)
    } yield (deployments, source)) onComplete {
      case Success((deployments, source)) ⇒ sendTo ! SynchronizeDeployments(deployments, source)
      case Failure(error)                 ⇒ throwException(InternalServerError(error))
    }

    (for {
      gateways ← allArtifacts[Gateway]
      source ← Future.successful(List.empty[String])
    } yield (gateways, source)) onComplete {
      case Success((gateways, source)) ⇒ sendTo ! SynchronizeGateways(gateways, source)
      case Failure(error)              ⇒ reportException(InternalServerError(error))
    }

    //    val sendTo = self
    //    implicit val timeout = PersistenceActor.timeout
    //    (for {
    //      gateways ← allArtifacts[Gateway]
    //      deployments ← allArtifacts[Deployment]
    //    } yield (gateways, deployments)) onComplete {
    //      case Success((gateways, deployments)) ⇒ sendTo ! Synchronize(deployments, gateways)
    //      case Failure(error)                   ⇒ throwException(InternalServerError(error))
    //    }
  }

  //  private def readerOf(`type`: String): Option[YamlReader[_ <: Artifact]] = Map(
  //    "gateways" -> DeployedGatewayReader,
  //    "deployments" -> DeploymentReader,
  //    "breeds" -> BreedReader,
  //    "blueprints" -> BlueprintReader,
  //    "slas" -> SlaReader,
  //    "scales" -> ScaleReader,
  //    "escalations" -> EscalationReader,
  //    "routings" -> RouteReader,
  //    "filters" -> FilterReader,
  //    "rewrites" -> FilterReader,
  //    "workflows" -> WorkflowReader,
  //    "scheduled-workflows" -> ScheduledWorkflowReader
  //  ).get(`type`)
}
