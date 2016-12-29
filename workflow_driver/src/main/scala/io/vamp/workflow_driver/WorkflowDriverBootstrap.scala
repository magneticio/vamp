package io.vamp.workflow_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.{ ActorBootstrap, IoC }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassProvider
import io.vamp.workflow_driver.notification.{ UnsupportedWorkflowDriverError, WorkflowDriverNotificationProvider }

class WorkflowDriverBootstrap extends ActorBootstrap with WorkflowDriverNotificationProvider {

  private val types = Config.string("vamp.workflow-driver.type").toLowerCase.split(',').map(_.trim)

  def createActors(implicit actorSystem: ActorSystem) = {

    val drivers = types.filterNot(_ == "none").map { name ⇒
      name → ClassProvider.find[WorkflowDriver](name)
    }.collect {
      case (_, Some(clazz)) ⇒ clazz
      case (value, None)    ⇒ throwException(UnsupportedWorkflowDriverError(value))
    }.map(_.getConstructor(classOf[ActorSystem]).newInstance(actorSystem)).toList :+ new NoneWorkflowDriver

    IoC.createActor[WorkflowDriverActor](drivers) :: Nil
  }
}
