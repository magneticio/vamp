package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import akka.pattern.ask
import io.vamp.common.Config
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriverActor, ContainerService }
import io.vamp.model.artifact.DeploymentService.Status
import io.vamp.model.artifact.DeploymentService.Status.Intention
import io.vamp.model.artifact.DeploymentService.Status.Phase._
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentValueResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.operation.notification.{ DeploymentServiceError, OperationNotificationProvider }
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }

import scala.concurrent.duration.FiniteDuration

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[DeploymentSynchronizationActor] ! SynchronizeAll
}

object DeploymentSynchronizationActor {

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

  val deploymentTimeout = Config.duration("vamp.operation.synchronization.timeout.ready-for-deployment")
  val undeploymentTimeout = Config.duration("vamp.operation.synchronization.timeout.ready-for-undeployment")
}

class DeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentValueResolver with OperationNotificationProvider {

  import DeploymentSynchronizationActor._

  private implicit val timeout = PersistenceActor.timeout()

  def receive: Receive = {
    case SynchronizeAll                        ⇒ synchronize()
    case cs: ContainerService                  ⇒ synchronize(cs)
    case (d: Deployment, cs: ContainerService) ⇒ synchronize(d, cs)
    case _                                     ⇒
  }

  private def synchronize() = {
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
    actorFor[PersistenceActor] ? PersistenceActor.Read(containerService.deployment.name, classOf[Deployment]) foreach {
      case Some(deployment: Deployment) ⇒ self ! (deployment → containerService)
      case _                            ⇒
    }
  }

  private def synchronize(deployment: Deployment, containerService: ContainerService): Unit = {
    def sendTo(actor: ActorRef, deployment: Deployment) = {
      deployment.service(containerService.service.breed).foreach { service ⇒
        actor ! SingleDeploymentSynchronizationActor.Synchronize(containerService.copy(deployment = deployment, service = service))
      }
    }
    val name = s"${self.path.name}-${containerService.deployment.lookupName}"
    context.child(name) match {
      case Some(actor) ⇒ sendTo(actor, deployment)
      case None        ⇒ sendTo(context.actorOf(Props(classOf[SingleDeploymentSynchronizationActor]), name), deployment)
    }
  }

  private def withError(service: DeploymentService): Boolean = service.status.phase.isInstanceOf[Failed]

  private def timedOut(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Boolean = {

    def checkTimeout(duration: FiniteDuration) = {
      val timed = duration.toNanos != 0 && OffsetDateTime.now().minus(duration.toSeconds, ChronoUnit.SECONDS).isAfter(service.status.since)
      if (timed) {
        val notification = DeploymentServiceError(deployment, service)
        reportException(notification)
        actorFor[PersistenceActor] ! PersistenceActor.UpdateDeploymentServiceStatus(deployment, cluster, service, Status(service.status.intention, Failed(notification)))
      }
      timed
    }

    !service.status.isDone && (service.status.intention match {
      case Intention.Deployment   ⇒ checkTimeout(deploymentTimeout())
      case Intention.Undeployment ⇒ checkTimeout(undeploymentTimeout())
      case _                      ⇒ false
    })
  }
}
