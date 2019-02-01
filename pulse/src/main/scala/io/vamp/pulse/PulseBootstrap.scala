package io.vamp.pulse

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }
import io.vamp.common.akka.ActorBootstrap
import io.vamp.pulse.notification.{ PulseNotificationProvider, UnsupportedPulseDriverError }

object PulseBootstrap {
  def `type`()(implicit namespace: Namespace) = Config.string("vamp.pulse.type")().toLowerCase
}

class PulseBootstrap extends ActorBootstrap with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    implicit val executionContext = actorSystem.dispatcher
    info(s"Pulse: ${PulseBootstrap.`type`()}")
    PulseBootstrap.`type`() match {
      case "nats" ⇒
        for {
          pulseActor ← alias[PulseActor](PulseBootstrap.`type`(), (`type`: String) ⇒ {
            throwException(UnsupportedPulseDriverError(`type`))
          })
          pulseActorSupport ← alias[PulseActorSupport]("elasticsearch", (`type`: String) ⇒ {
            throwException(UnsupportedPulseDriverError(`type`))
          })
          pulseActorPublisher ← alias[PulseActorPublisher]("natspublisher", (`type`: String) ⇒ {
            throwException(UnsupportedPulseDriverError(`type`))
          })
        } yield pulseActor :: pulseActorSupport :: pulseActorPublisher :: Nil
      case _ ⇒
        alias[PulseActor](PulseBootstrap.`type`(), (`type`: String) ⇒ {
          throwException(UnsupportedPulseDriverError(`type`))
        }).map(_ :: Nil)(actorSystem.dispatcher)
    }
  }
}
