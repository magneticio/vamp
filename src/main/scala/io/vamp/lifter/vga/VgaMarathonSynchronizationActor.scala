package io.vamp.lifter.vga

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver.marathon._
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInfo, Docker, DockerApp }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.vga.VgaMarathonSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport }

import scala.concurrent.Future

class VgaMarathonSynchronizationSchedulerActor extends SchedulerActor with LifterNotificationProvider {

  def tick() = IoC.actorFor[VgaMarathonSynchronizationActor] ! SynchronizeAll
}

object VgaMarathonSynchronizationActor {

  sealed trait VgaMarathonMessages

  object SynchronizeAll extends VgaMarathonMessages

  case class Synchronize(info: MarathonDriverInfo, app: Option[App]) extends VgaMarathonMessages

}

class VgaMarathonSynchronizationActor extends VgaSynchronizationActor with ArtifactSupport with ArtifactPaginationSupport {

  import VgaMarathonSynchronizationActor._

  def receive = {
    case SynchronizeAll         ⇒ synchronize()
    case Synchronize(info, app) ⇒ synchronize(info, app)
    case _                      ⇒
  }

  private def synchronize() = {
    val actor = self
    (IoC.actorFor[ContainerDriverActor] ? InfoRequest) flatMap {
      case ContainerInfo("marathon", info: MarathonDriverInfo) ⇒
        (IoC.actorFor[ContainerDriverActor] ? RetrieveDockerApp(id)) map {
          case Some(app: App) ⇒ actor ! Synchronize(info, Option(app))
          case None           ⇒ actor ! Synchronize(info, None)
          case any            ⇒
        }
      case any ⇒ Future.successful(any)
    }
  }

  private def synchronize(info: MarathonDriverInfo, app: Option[App]) = {
    log.debug(s"Checking number of VGA's.")

    val count = info.mesos.slaves match {
      case slaves: List[_] ⇒ slaves.size
      case _               ⇒ 0
    }

    val instances = app.map(_.instances).getOrElse(0)

    if (count != instances) {
      log.info(s"Initiating VGA deployment, number of instances: $count")

      if (count > 0)
        IoC.actorFor[ContainerDriverActor] ! DeployDockerApp(request(count), update = instances != 0)
      else
        IoC.actorFor[ContainerDriverActor] ! UndeployDockerApp(id)
    }
  }

  private def request(instances: Int) = {
    DockerApp(
      id = id,
      container = Option(
        Docker(
          image = image,
          portMappings = ports,
          parameters = Nil,
          privileged = true,
          network = network
        )
      ),
      instances = instances,
      cpu = cpu,
      memory = mem,
      environmentVariables = Map(),
      command = Nil,
      arguments = command,
      constraints = List(List("hostname", "UNIQUE"))
    )
  }
}
