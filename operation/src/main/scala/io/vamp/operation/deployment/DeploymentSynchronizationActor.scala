package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import akka.util.Timeout
import io.vamp.common.{ Config, Id }
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
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.DeploymentPersistenceOperations

import scala.concurrent.Future
import scala.concurrent.duration._
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

class DeploymentSynchronizationActor extends CommonSupportForActors with DeploymentValueResolver with OperationNotificationProvider with VampJsonFormats {

  import DeploymentSynchronizationActor._

  private implicit val timeout = Timeout(5.second)

  def receive: Receive = {
    case SynchronizeAll                        ⇒ synchronize()
    case cs: ContainerService                  ⇒ synchronize(cs)
    case (d: Deployment, cs: ContainerService) ⇒ synchronize(d, cs)
    case _                                     ⇒
  }

  private def synchronize() = {
    VampPersistence().getAll[Deployment]().map(_.response).map { deployments ⇒
      val deploymentServices = deployments.map { deployment ⇒
        val services = deployment.clusters.flatMap { cluster ⇒
          cluster.services.collect {
            case service if !withError(service) && !timedOut(deployment, cluster, service) ⇒ service
          }
        }
        DeploymentServices(deployment, services)
      }
      actorFor[ContainerDriverActor] ! ContainerDriverActor.Get(deploymentServices)
    }
  }

  private def synchronize(containerService: ContainerService): Unit = {
    VampPersistence().read[Deployment](Id[Deployment](containerService.deployment.name)) foreach {
      case deployment: Deployment ⇒ self ! (deployment → containerService)
      case _                      ⇒
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
      val timed = duration.toNanos != 0 && OffsetDateTime.now().minus(duration.toSeconds, ChronoUnit.SECONDS).isAfter(service.status.phase.since)
      if (timed) {
        val notification = DeploymentServiceError(deployment, service)
        reportException(notification)

        val serviceStatus = Status(
          service.status.intention,
          Failed(s"Deployment service error for deployment ${deployment.name} and service ${service.breed.name}."))

        DeploymentPersistenceOperations.updateServiceStatus(deployment, cluster, service, serviceStatus)
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
