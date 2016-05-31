package io.vamp.workflow_driver

import akka.actor.ActorSystem
import akka.pattern.ask
import io.vamp.common.akka.IoC
import io.vamp.container_driver.{ Container, ContainerDriverActor, Docker }
import io.vamp.container_driver.marathon.MarathonDriverActor.{ AllApps, DeployApp, RetrieveApp, UndeployApp }
import io.vamp.container_driver.marathon.{ MarathonApp, MarathonDriverActor }
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.workflow.{ DaemonTrigger, DefaultWorkflow, ScheduledWorkflow }

import scala.concurrent.Future

class MarathonWorkflowDriver(implicit actorSystem: ActorSystem) extends WorkflowDriver {

  private implicit val timeout = ContainerDriverActor.timeout
  private implicit val executionContext = actorSystem.dispatcher

  private val namePrefix = "vamp/workflow-"

  override def info: Future[Map[_, _]] = Future.successful(Map("marathon" -> Map("url" -> MarathonDriverActor.marathonUrl)))

  override def all(): Future[List[WorkflowInstance]] = IoC.actorFor[MarathonDriverActor] ? AllApps(app ⇒ app.id.startsWith(namePrefix)) map {
    case list: List[_] ⇒ list.filter(_.isInstanceOf[MarathonApp]).map(app ⇒ WorkflowInstance(app.asInstanceOf[MarathonApp].id))
    case _             ⇒ Nil
  }

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow if workflow.trigger == DaemonTrigger ⇒
      val marathonApp = app(workflow)
      IoC.actorFor[MarathonDriverActor] ? RetrieveApp(marathonApp.id) map {
        case Some(_) ⇒ IoC.actorFor[MarathonDriverActor] ? DeployApp(marathonApp, update = true)
        case _       ⇒ IoC.actorFor[MarathonDriverActor] ? DeployApp(marathonApp, update = false)
      }
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow if workflow.trigger == DaemonTrigger ⇒
      IoC.actorFor[MarathonDriverActor] ? RetrieveApp(name(workflow)) map {
        case Some(_) ⇒ IoC.actorFor[MarathonDriverActor] ? UndeployApp(name(workflow))
        case _       ⇒ Future.successful(true)
      }
  }

  private def app(scheduledWorkflow: ScheduledWorkflow): MarathonApp = {

    val workflow = scheduledWorkflow.workflow.asInstanceOf[DefaultWorkflow]
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]

    MarathonApp(
      id = s"$namePrefix${name(scheduledWorkflow)}",
      container = Option(
        Container(
          docker = Docker(
            image = workflow.containerImage.get,
            portMappings = Nil,
            parameters = Nil,
            privileged = true,
            network = "BRIDGE"
          )
        )
      ),
      instances = scale.instances,
      cpus = scale.cpu.value,
      mem = Math.round(scale.memory.value).toInt,
      env = Map("VAMP_KEY_VALUE_STORE_ROOT_PATH" -> WorkflowDriver.pathToString(scheduledWorkflow)),
      cmd = Option(workflow.command.get),
      args = Nil,
      constraints = Nil
    )
  }

  private def name(scheduledWorkflow: ScheduledWorkflow) = {
    if (scheduledWorkflow.name.matches("^[\\w-]+$")) scheduledWorkflow.name else scheduledWorkflow.lookupName
  }
}
