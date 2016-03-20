package io.vamp.lifter.vga

import io.vamp.common.akka._
import io.vamp.lifter.notification.LifterNotificationProvider
import io.vamp.lifter.vga.VgaSynchronizationActor.SynchronizeAll
import io.vamp.persistence.db.{ ArtifactPaginationSupport, ArtifactSupport }

import scala.concurrent.Future
import scala.language.postfixOps

class VgaSynchronizationSchedulerActor extends SchedulerActor with LifterNotificationProvider {

  def tick() = IoC.actorFor[VgaSynchronizationActor] ! SynchronizeAll
}

object VgaSynchronizationActor {

  sealed trait VgaMessages

  object SynchronizeAll extends VgaMessages

}

class VgaSynchronizationActor extends CommonSupportForActors with ArtifactSupport with ArtifactPaginationSupport with LifterNotificationProvider {

  import VgaSynchronizationActor._

  def receive = {
    case SynchronizeAll ⇒ synchronize()
    case _              ⇒
  }

  private def synchronize() = Future.successful(true)
}
