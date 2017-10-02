package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.ClassMapper
import io.vamp.common.akka.IoC
import io.vamp.container_driver.marathon.MarathonDriverActor

import scala.concurrent.Future

class MarathonWorkflowActorMapper extends ClassMapper {
  val name = "marathon"
  val clazz: Class[_] = classOf[MarathonWorkflowActor]
}

class MarathonWorkflowActor extends DaemonWorkflowDriver {

  override protected def info: Future[Map[_, _]] = Future.successful(Map("marathon" → Map("url" → MarathonDriverActor.marathonUrl())))

  override protected def driverActor: ActorRef = IoC.actorFor[MarathonDriverActor]
}
