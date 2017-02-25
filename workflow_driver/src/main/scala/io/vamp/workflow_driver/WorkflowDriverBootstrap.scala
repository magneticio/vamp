package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import akka.util.Timeout
import io.vamp.common.{ ClassProvider, Config, Namespace }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

import scala.concurrent.{ ExecutionContext, Future }

class WorkflowDriverBootstrap extends ActorBootstrap with WorkflowDriverNotificationProvider {

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout) = {
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    val types = Config.string("vamp.workflow-driver.type")().toLowerCase.split(',').map(_.trim).toList

    val drivers: Future[List[ActorRef]] = Future.sequence(types.map { name ⇒
      ClassProvider.find[WorkflowDriver](name) match {
        case Some(clazz) ⇒ IoC.createActor(clazz)
        case None        ⇒ throwException(UnsupportedWorkflowDriverError(name))
      }
    })

    types.foreach { t ⇒ logger.info(s"Workflow driver: $t") }
    drivers.flatMap(IoC.createActor[WorkflowDriverActor](_)).map(_ :: Nil)
  }
}
