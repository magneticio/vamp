package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.ask
import io.vamp.container_driver.DockerAppDriver.{ AllDockerApps, DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver.{ ContainerDriverActor, Docker, DockerApp }
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.workflow.{ DaemonTrigger, DefaultWorkflow, ScheduledWorkflow }

import scala.concurrent.Future

abstract class DaemonWorkflowDriver(implicit actorSystem: ActorSystem) extends WorkflowDriver {

  private implicit val timeout = ContainerDriverActor.timeout
  private implicit val executionContext = actorSystem.dispatcher

  protected def namePrefix: String

  protected def driverActor: ActorRef

  override def all(): Future[List[WorkflowInstance]] = driverActor ? AllDockerApps(app ⇒ app.id.startsWith(namePrefix)) map {
    case list: List[_] ⇒ list.filter(_.isInstanceOf[DockerApp]).map(app ⇒ WorkflowInstance(app.asInstanceOf[DockerApp].id))
    case _             ⇒ Nil
  }

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow if workflow.trigger == DaemonTrigger ⇒
      val dockerApp = app(workflow)
      driverActor ? RetrieveDockerApp(dockerApp.id) map {
        case Some(_) ⇒ driverActor ? DeployDockerApp(dockerApp, update = true)
        case _       ⇒ driverActor ? DeployDockerApp(dockerApp, update = false)
      }
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow if workflow.trigger == DaemonTrigger ⇒
      driverActor ? RetrieveDockerApp(name(workflow)) map {
        case Some(_) ⇒ driverActor ? UndeployDockerApp(name(workflow))
        case _       ⇒ Future.successful(true)
      }
  }

  private def app(scheduledWorkflow: ScheduledWorkflow): DockerApp = {

    val workflow = scheduledWorkflow.workflow.asInstanceOf[DefaultWorkflow]
    val scale = scheduledWorkflow.scale.get.asInstanceOf[DefaultScale]

    DockerApp(
      id = name(scheduledWorkflow),
      container = Option(
        Docker(
          image = workflow.containerImage.get,
          portMappings = Nil,
          parameters = Nil,
          privileged = true,
          network = "BRIDGE"
        )
      ),
      instances = scale.instances,
      cpu = scale.cpu.value,
      memory = Math.round(scale.memory.value).toInt,
      environmentVariables = Map(
        "VAMP_URL" -> WorkflowDriver.vampUrl,
        "VAMP_KEY_VALUE_STORE_ROOT_PATH" -> WorkflowDriver.pathToString(scheduledWorkflow)
      ),
      command = Option(workflow.command.get),
      arguments = Nil,
      constraints = Nil
    )
  }

  private def name(scheduledWorkflow: ScheduledWorkflow) = {
    val id = if (scheduledWorkflow.name.matches("^[\\w-]+$")) scheduledWorkflow.name else scheduledWorkflow.lookupName
    s"$namePrefix$id"
  }
}
