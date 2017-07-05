package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.ClassMapper
import io.vamp.common.akka.IoC
import io.vamp.container_driver.docker.DockerDriverActor

import scala.concurrent.Future

class DockerWorkflowActorMapper extends ClassMapper {
  val name = "docker"
  val clazz = classOf[DockerWorkflowActor]
}

class DockerWorkflowActor extends DaemonWorkflowDriver {

  override protected def info: Future[Map[_, _]] = Future.successful(Map("docker" → None))

  override protected def driverActor: ActorRef = IoC.actorFor[DockerDriverActor]
}
