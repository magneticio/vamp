package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorRefFactory }
import akka.pattern.ask
import io.vamp.common.akka.ActorRefFactoryExecutionContextProvider
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver.{ ContainerDriverActor, Docker, DockerApp }
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.workflow.{ DaemonTrigger, DefaultWorkflow, ScheduledWorkflow }
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled

import scala.concurrent.Future

abstract class DaemonWorkflowDriver(implicit override val actorRefFactory: ActorRefFactory) extends WorkflowDriver with ActorRefFactoryExecutionContextProvider {

  private implicit val timeout = ContainerDriverActor.timeout

  protected def namePrefixConfig: String

  protected def driverActor: ActorRef

  private lazy val namePrefix = WorkflowDriver.config.string(namePrefixConfig)

  override def request(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]): Unit = scheduledWorkflows.foreach { scheduled ⇒
    if (scheduled.trigger == DaemonTrigger) {
      driverActor ? RetrieveDockerApp(name(scheduled)) map {
        case Some(_) ⇒ replyTo ! Scheduled(scheduled, Option(WorkflowInstance(scheduled.name)))
        case _       ⇒ replyTo ! Scheduled(scheduled, None)
      }
    }
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

  protected def app(scheduledWorkflow: ScheduledWorkflow): DockerApp = {

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
      command = workflow.command.map(_.split(" ").toList).getOrElse(Nil),
      arguments = Nil,
      constraints = Nil
    )
  }

  private def name(scheduledWorkflow: ScheduledWorkflow) = {
    val id = if (scheduledWorkflow.name.matches("^[\\w-]+$")) scheduledWorkflow.name else scheduledWorkflow.lookupName
    s"$namePrefix$id"
  }
}
