package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.IoC
import io.vamp.common.spi.ClassMapper
import io.vamp.container_driver.kubernetes.KubernetesDriverActor

import scala.concurrent.Future

class KubernetesWorkflowDriverMapper extends ClassMapper {
  val name = "kubernetes"
  val clazz = classOf[KubernetesWorkflowDriver]
}

class KubernetesWorkflowDriver(implicit override val actorSystem: ActorSystem) extends DaemonWorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map("kubernetes" → Map("url" → KubernetesDriverActor.url)))

  override protected def driverActor: ActorRef = IoC.actorFor[KubernetesDriverActor]
}
