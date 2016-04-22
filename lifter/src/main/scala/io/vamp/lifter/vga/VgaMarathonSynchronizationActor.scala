package io.vamp.lifter.vga

import akka.pattern.ask
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.marathon.MarathonDriverActor.{ DeployApp, RetrieveApp, UndeployApp }
import io.vamp.container_driver.marathon.MarathonDriverInfo
import io.vamp.container_driver.marathon.api.{ App, Container, Docker, MarathonApp }
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInfo }
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.vga.VgaMarathonSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport }

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.postfixOps

class VgaMarathonSynchronizationSchedulerActor extends SchedulerActor with LifterNotificationProvider {

  def tick() = IoC.actorFor[VgaMarathonSynchronizationActor] ! SynchronizeAll
}

object VgaMarathonSynchronizationActor {

  sealed trait VgaMessages

  object SynchronizeAll extends VgaMessages

  case class Synchronize(info: MarathonDriverInfo, app: Option[App]) extends VgaMessages

}

class VgaMarathonSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with LifterNotificationProvider {

  import VgaMarathonSynchronizationActor._

  private implicit val timeout = ContainerDriverActor.timeout

  private val configuration = ConfigFactory.load().getConfig("vamp.lifter.vamp-gateway-agent.synchronization")

  private val id = configuration.getString("id")

  private val container = configuration.getString("container-image")
  private val arguments = configuration.getStringList("container-arguments").asScala.toList

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
          portMappings = Nil,
          parameters = Nil,
          privileged = true,
          network = "HOST"
        )
      )
    ),
    instances = instances,
    cpus = 0.1,
    mem = 128,
    env = Map(),
    cmd = None,
    args = arguments,
    constraints = List(List("hostname", "UNIQUE"))
  )
}
