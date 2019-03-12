package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.actor.{ ActorRef, Props }
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.container_driver.ContainerDriverActor.DeploymentServices
import io.vamp.container_driver.{ ContainerDriverActor, ContainerService, ServiceEqualityRequest }
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

  def tick(): Unit = IoC.actorFor[DeploymentSynchronizationActor] ! SynchronizeAll
}

object DeploymentSynchronizationActor {

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

  val deploymentTimeout: ConfigMagnet[FiniteDuration] = Config.duration("vamp.operation.synchronization.timeout.ready-for-deployment")

  val undeploymentTimeout: ConfigMagnet[FiniteDuration] = Config.duration("vamp.operation.synchronization.timeout.ready-for-undeployment")

  val checkDeployable: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.deployable")

  val checkPorts: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.ports")

  val checkEnvironmentVariables: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.environment-variables")

  val checkCpu: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.cpu")

  val checkMemory: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.memory")

  val checkInstances: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.instances")

  val checkHealth: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.synchronization.check.health-checks")
}

class DeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentValueResolver with OperationNotificationProvider {

  import DeploymentSynchronizationActor._

  private implicit val timeout: Timeout = PersistenceActor.timeout()

  def receive: Receive = {
    case SynchronizeAll                        ⇒ synchronize()
    case cs: ContainerService                  ⇒ synchronize(cs)
    case (d: Deployment, cs: ContainerService) ⇒ synchronize(d, cs) // Look at : io.vamp.operation.deployment.DeploymentSynchronizationActor.synchronize
    case _                                     ⇒
  }

  private def synchronize(): Unit = {
    log.debug(s"deployment synchronization: ${namespace.name}")
    forAll(allArtifacts[Deployment], { deployments ⇒
      val deploymentServices = deployments.map { deployment ⇒
        val services = deployment.clusters.flatMap { cluster ⇒
          cluster.services.collect {
            case service if !withError(service) && !timedOut(deployment, cluster, service) ⇒ service
          }
        }
        DeploymentServices(deployment, services)
      }
      actorFor[ContainerDriverActor] ! ContainerDriverActor.Get(
        deploymentServices,
        ServiceEqualityRequest(
          deployable = checkDeployable(),
          ports = checkPorts(),
          environmentVariables = checkEnvironmentVariables(),
          health = checkHealth()
        )
      )
    }: (List[Deployment]) ⇒ Unit)
  }

  private def synchronize(containerService: ContainerService): Unit = {
    log.debug(s"[DeploymentSynchronizationActor] Deployment Synchronization started for ${containerService.deployment.name}")
    actorFor[PersistenceActor] ? PersistenceActor.Read(containerService.deployment.name, classOf[Deployment]) foreach {
      case Some(deployment: Deployment) ⇒ {
        log.debug(s"[DeploymentSynchronizationActor] Deployment Synchronization triggered for for ${containerService.deployment.name} network: ${containerService.service.network.getOrElse("Empty")}")
        self ! (deployment → containerService)
      }
      case _ ⇒ log.debug(s"[DeploymentSynchronizationActor] Read from persistence got an unexpected data ${containerService.deployment.name}")
    }
  }

  private def synchronize(deployment: Deployment, containerService: ContainerService): Unit = {
    def sendTo(actor: ActorRef, deployment: Deployment): Unit = {
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
