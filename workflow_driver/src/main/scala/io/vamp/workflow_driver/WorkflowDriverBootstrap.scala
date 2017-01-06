package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassProvider
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

class WorkflowDriverBootstrap extends ActorBootstrap with WorkflowDriverNotificationProvider {

  private val types = Config.string("vamp.workflow-driver.type").toLowerCase.split(',').map(_.trim).toList

  def createActors(implicit actorSystem: ActorSystem) = {

    val drivers: List[ActorRef] = types.map { name ⇒
      ClassProvider.find[WorkflowDriver](name) match {
        case Some(clazz) ⇒
          IoC.createActor(clazz)
          IoC.actorFor(clazz)
        case None ⇒ throwException(UnsupportedWorkflowDriverError(name))
      }
    }

    IoC.createActor[WorkflowDriverActor](drivers) :: Nil
  }
}
