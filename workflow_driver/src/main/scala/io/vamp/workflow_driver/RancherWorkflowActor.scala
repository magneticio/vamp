package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.akka.IoC
import io.vamp.common.spi.ClassMapper
import io.vamp.container_driver.rancher.RancherDriverActor

import scala.concurrent.Future

class RancherWorkflowActorMapper extends ClassMapper {
  val name = "rancher"
  val clazz = classOf[RancherWorkflowActor]
}

class RancherWorkflowActor extends DaemonWorkflowDriver {

  override protected def info: Future[Map[_, _]] = Future.successful(Map("rancher" → Map("url" → RancherDriverActor.rancherUrl)))

  override protected def driverActor: ActorRef = IoC.actorFor[RancherDriverActor]
}
