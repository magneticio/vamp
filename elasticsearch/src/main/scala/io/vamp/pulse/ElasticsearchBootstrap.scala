package io.vamp.pulse

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC.logger
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.pulse.notification.PulseNotificationProvider

import scala.concurrent.{ ExecutionContext, Future }

class ElasticsearchBootstrap
    extends ActorBootstrap
    with PulseNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem,
                   namespace: Namespace,
                   timeout: Timeout): Future[List[ActorRef]] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    Future.sequence(IoC.createActor[PulseInitializationActor] :: Nil)
  }

  override def start(implicit actorSystem: ActorSystem,
                     namespace: Namespace,
                     timeout: Timeout): Future[Unit] = {
    implicit val executionContext: ExecutionContext = actorSystem.dispatcher
    logger.info(s"Starting pulse initialization actor")
    super.start.flatMap {
      _ => {
        logger.info(s"Initialization actor created")
        IoC.actorFor[PulseInitializationActor] ! PulseInitializationActor.Initialize
        logger.info(s"Pulse initialized")
        Future.unit
      }
    }
  }
}
