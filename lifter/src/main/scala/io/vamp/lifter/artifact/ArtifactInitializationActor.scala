package io.vamp.lifter.artifact

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.lifter.notification.LifterNotificationProvider

object ArtifactInitializationActor {
  private[artifact] object Load
}

class ArtifactInitializationActor extends CommonSupportForActors with LifterNotificationProvider {

  import ArtifactInitializationActor._

  def receive = {
    case Load ⇒ load()
    case _    ⇒
  }

  override def preStart(): Unit = self ! Load

  private def load() = {

  }
}

