package io.vamp.lifter.vga

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.config.Config
import io.vamp.container_driver.ContainerDriverBootstrap
import io.vamp.lifter.artifact.ArtifactLoader
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.operation.notification.InternalServerError
import io.vamp.persistence.db.PersistenceActor

object VgaInitializationActor {

  private[vga] object DeployVga

}

class VgaInitializationActor extends ArtifactLoader with CommonSupportForActors with LifterNotificationProvider {

  import VgaInitializationActor._

  implicit val timeout = PersistenceActor.timeout

  private val config = Config.config("vamp.lifter.vamp-gateway-agent")

  private val postpone = config.duration("postpone")

  private val namePrefix = "vamp-gateway-agent-"

  def receive = {
    case DeployVga ⇒ deploy()
    case _         ⇒
  }

  override def preStart(): Unit = {
    try {
      context.system.scheduler.scheduleOnce(postpone, self, DeployVga)
    } catch {
      case t: Throwable ⇒ reportException(InternalServerError(t))
    }
  }

  private def deploy() = {
    val name = s"$namePrefix${ContainerDriverBootstrap.`type`}"
    loadResources(force = true)(s"breeds/$name.js" :: s"workflows/$name.yml" :: Nil)
  }
}
