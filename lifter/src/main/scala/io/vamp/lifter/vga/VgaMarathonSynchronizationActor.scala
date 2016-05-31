package io.vamp.lifter.vga

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.marathon.MarathonDriverActor.{ DeployApp, RetrieveApp, UndeployApp }
import io.vamp.container_driver.marathon._
import io.vamp.container_driver.{ Container, ContainerDriverActor, ContainerInfo, Docker }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.vga.VgaMarathonSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport }

import scala.concurrent.Future
import scala.language.postfixOps

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
        (IoC.actorFor[ContainerDriverActor] ? RetrieveApp(id)) map {
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
        IoC.actorFor[ContainerDriverActor] ! DeployApp(request(count), update = instances != 0)
      else
        IoC.actorFor[ContainerDriverActor] ! UndeployApp(id)
    }
  }

  private def request(instances: Int) = MarathonApp(
    id = id,
    container = Option(
      Container(
        docker = Docker(
          image = container,
          portMappings = ports,
          parameters = Nil,
          privileged = true,
          network = "HOST"
        )
      )
    ),
    instances = instances,
    cpus = cpu,
    mem = mem,
    env = Map(),
    cmd = None,
    args = arguments,
    constraints = List(List("hostname", "UNIQUE"))
  )
}
