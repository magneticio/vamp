package io.vamp.lifter.vga

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp }
import io.vamp.container_driver.{ ContainerDriverActor, Docker, DockerApp }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.vga.VgaRancherSynchronizationActor.Synchronize

class VgaRancherSynchronizationSchedulerActor extends SchedulerActor with LifterNotificationProvider {

  def tick() = IoC.actorFor[VgaRancherSynchronizationActor] ! Synchronize
}

object VgaRancherSynchronizationActor {

  sealed trait VgaRancherMessages

  object Synchronize extends VgaRancherMessages

}

class VgaRancherSynchronizationActor extends VgaSynchronizationActor {

  import VgaRancherSynchronizationActor._

  def receive = {
    case Synchronize ⇒ synchronize()
    case _           ⇒
  }

  private def synchronize() = {
    val actor = IoC.actorFor[ContainerDriverActor]
    actor ? RetrieveDockerApp(id) map {
      case Some(_) ⇒
      case None    ⇒ actor ! DeployDockerApp(request, update = false)
      case _       ⇒
    }
  }

  private def request = {
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
      instances = 1,
      cpu = cpu,
      memory = mem,
      environmentVariables = Map(),
      command = command,
      labels = Map("io.rancher.scheduler.global" -> "true")
    )
  }
}
