package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriverActor, ContainerService }
import io.vamp.model.artifact.DeploymentService.State.Intention
import io.vamp.model.artifact.DeploymentService.State.Step._
import io.vamp.model.artifact.DeploymentService._
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.operation.notification.{ DeploymentServiceError, OperationNotificationProvider }
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }

import scala.language.postfixOps

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[DeploymentSynchronizationActor] ! SynchronizeAll
}

object DeploymentSynchronizationActor {

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

}

class DeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentTraitResolver with OperationNotificationProvider {

  import DeploymentSynchronizationActor._

  def receive: Receive = {
    case SynchronizeAll       ⇒ synchronize()
    case cs: ContainerService ⇒ synchronize(cs)
    case _                    ⇒
  }

  private def synchronize() = {
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[Deployment] foreach { deployments ⇒
      val deploymentServices = deployments.filterNot(withError).map { deployment ⇒
        DeploymentServices(deployment, deployment.clusters.flatMap(_.services))
      }
      actorFor[ContainerDriverActor] ! ContainerDriverActor.Get(deploymentServices)
    }
  }

  private def synchronize(containerService: ContainerService): Unit = {

    def sendTo(actor: ActorRef) = actor ! SingleDeploymentSynchronizationActor.Synchronize(containerService)

    val name = s"deployment-synchronization-${containerService.deployment.lookupName}"
    context.child(name) match {
      case Some(actor) ⇒ sendTo(actor)
      case None        ⇒ sendTo(context.actorOf(Props(classOf[SingleDeploymentSynchronizationActor]), name))
    }
  }

  private def withError(deployment: Deployment): Boolean = {
    lazy val now = OffsetDateTime.now()
    lazy val config = Config.config("vamp.operation.synchronization.timeout")
    lazy val deploymentTimeout = config.duration("ready-for-deployment")
    lazy val undeploymentTimeout = config.duration("ready-for-undeployment")

    def handleTimeout(service: DeploymentService) = {
      val notification = DeploymentServiceError(deployment, service)
      reportException(notification)
      actorFor[PersistenceActor] ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster ⇒ cluster.copy(services = cluster.services.map({ s ⇒
        if (s.breed.name == service.breed.name) {
          s.copy(state = State(s.state.intention, Failure(notification)))
        } else s
      })))))
      true
    }

    deployment.clusters.flatMap(_.services).exists { service ⇒
      service.state.intention match {
        case Intention.Deploy   ⇒ if (!service.state.isDone && now.minus(deploymentTimeout.toSeconds, ChronoUnit.SECONDS).isAfter(service.state.since)) handleTimeout(service) else false
        case Intention.Undeploy ⇒ if (!service.state.isDone && now.minus(undeploymentTimeout.toSeconds, ChronoUnit.SECONDS).isAfter(service.state.since)) handleTimeout(service) else false
        case _                  ⇒ service.state.step.isInstanceOf[Failure]
      }
    }
  }
}
