package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriverActor, ContainerService }
import io.vamp.model.artifact.DeploymentService.Status
import io.vamp.model.artifact.DeploymentService.Status.Intention
import io.vamp.model.artifact.DeploymentService.Status.Phase._
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.operation.notification.{ DeploymentServiceError, OperationNotificationProvider }
import io.vamp.persistence.db.{ ArtifactPaginationSupport, DevelopmentPersistenceMessages, PersistenceActor }

import scala.concurrent.duration.FiniteDuration

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[DeploymentSynchronizationActor] ! SynchronizeAll
}

object DeploymentSynchronizationActor {

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

  val config = Config.config("vamp.operation.synchronization.timeout")
  val deploymentTimeout = config.duration("ready-for-deployment")
  val undeploymentTimeout = config.duration("ready-for-undeployment")
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
    forAll(allArtifacts[Deployment], { deployments ⇒

      val deploymentServices = deployments.map { deployment ⇒
        val services = deployment.clusters.flatMap { cluster ⇒
          cluster.services.collect {
            case service if !withError(service) && !timedOut(deployment, cluster, service) ⇒ service
          }
        }
        DeploymentServices(deployment, services)
      }

      actorFor[ContainerDriverActor] ! ContainerDriverActor.Get(deploymentServices)

    }: (List[Deployment]) ⇒ Unit)
  }

  private def synchronize(containerService: ContainerService): Unit = {
    def sendTo(actor: ActorRef) = actor ! SingleDeploymentSynchronizationActor.Synchronize(containerService)

    val name = s"deployment-synchronization-${containerService.deployment.lookupName}"
    context.child(name) match {
      case Some(actor) ⇒ sendTo(actor)
      case None        ⇒ sendTo(context.actorOf(Props(classOf[SingleDeploymentSynchronizationActor]), name))
    }
  }

  private def withError(service: DeploymentService): Boolean = service.status.phase.isInstanceOf[Failed]

  private def timedOut(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Boolean = {

    def checkTimeout(duration: FiniteDuration) = {
      val timed = duration.toNanos != 0 && OffsetDateTime.now().minus(duration.toSeconds, ChronoUnit.SECONDS).isAfter(service.status.since)
      if (timed) {
        val notification = DeploymentServiceError(deployment, service)
        reportException(notification)
        actorFor[PersistenceActor] ! DevelopmentPersistenceMessages.UpdateDeploymentServiceStatus(deployment, cluster, service, Status(service.status.intention, Failed(notification)))
      }
      timed
    }

    !service.status.isDone && (service.status.intention match {
      case Intention.Deployment   ⇒ checkTimeout(deploymentTimeout)
      case Intention.Undeployment ⇒ checkTimeout(undeploymentTimeout)
      case _                      ⇒ false
    })
  }
}
