package io.vamp.workflow_driver

import akka.actor.ActorRef
import akka.pattern.ask
import io.vamp.common.akka.IoC.actorFor
import io.vamp.container_driver.ContainerDriverActor.{ DeployWorkflow, GetWorkflow, UndeployWorkflow }
import io.vamp.container_driver.ContainerWorkflow
import io.vamp.model.artifact.{ DaemonSchedule, DefaultBreed, Instance, Workflow }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future

trait DaemonWorkflowDriver extends WorkflowDriver {

  protected def driverActor: ActorRef

  override def receive = super.receive orElse {
    case ContainerWorkflow(workflow, containers) ⇒
      artifactFor[DefaultBreed](workflow.breed).foreach { breed ⇒
        val instances = containers.map(_.instances.map { instance ⇒
          val ports = breed.ports.map(_.name) zip instance.ports
          Instance(instance.name, instance.host, ports.toMap, instance.deployed)
        }).getOrElse(Nil)
        actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, instances)
      }
    case _ ⇒
  }

  protected override def request(workflows: List[Workflow]): Unit = {
    workflows.foreach {
      case scheduled if scheduled.schedule == DaemonSchedule ⇒ driverActor ! GetWorkflow(scheduled, self)
      case _ ⇒
    }
  }

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒ driverActor ? DeployWorkflow(enrich(workflow), update = workflow.instances.nonEmpty)
    case _ ⇒ Future.successful(false)
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule && workflow.instances.nonEmpty ⇒ driverActor ? UndeployWorkflow(workflow)
    case _ ⇒ Future.successful(false)
  }
}
