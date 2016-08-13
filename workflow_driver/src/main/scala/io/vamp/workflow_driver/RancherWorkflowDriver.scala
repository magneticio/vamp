package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.IoC
import io.vamp.container_driver.rancher.RancherDriverActor

import scala.concurrent.Future

class RancherWorkflowDriver(implicit override val actorSystem: ActorSystem) extends DaemonWorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map("rancher" -> Map("url" -> RancherDriverActor.rancherUrl)))

  override protected def driverActor: ActorRef = IoC.actorFor[RancherDriverActor]
}
