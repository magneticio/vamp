package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.IoC
import io.vamp.container_driver.kubernetes.KubernetesDriverActor

import scala.concurrent.Future

class KubernetesWorkflowDriver(implicit actorSystem: ActorSystem) extends DaemonWorkflowDriver {

  protected val namePrefix = "vamp-workflow-"

  override def info: Future[Map[_, _]] = Future.successful(Map("docker" -> None))

  override protected def driverActor: ActorRef = IoC.actorFor[KubernetesDriverActor]
}
