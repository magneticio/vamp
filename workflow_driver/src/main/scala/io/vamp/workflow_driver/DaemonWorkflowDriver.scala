package io.vamp.workflow_driver

import akka.actor.ActorRef
import akka.pattern.ask
import io.vamp.container_driver.ContainerDriverActor.{DeployWorkflow, GetWorkflow, UndeployWorkflow}
import io.vamp.container_driver.ContainerWorkflow
import io.vamp.model.artifact.{DaemonSchedule, DefaultBreed, Instance, Workflow}
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

trait DaemonWorkflowDriver extends WorkflowDriver with VampJsonFormats{

  protected def driverActor: ActorRef

  override def receive = super.receive orElse {
    case ContainerWorkflow(workflow, containers, health, _) ⇒
      workflow.breed match {
        case breed: DefaultBreed ⇒
          VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(health = health))

          val instances = containers.map(_.instances.map { instance ⇒
            val ports = breed.ports.map(_.name) zip instance.ports
            Instance(instance.name, instance.host, ports.toMap, instance.deployed)
          }).getOrElse(Nil)
          if (workflow.instances != instances)
            VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(instances = instances))
        case _ ⇒
      }
    case _ ⇒
  }

  protected override def request(workflows: List[Workflow]): Unit = workflows.foreach(request)

  protected def request: PartialFunction[Workflow, Unit] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒ driverActor ! GetWorkflow(workflow, self)
    case _ ⇒
  }

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒ enrich(workflow, data).flatMap { enriched ⇒ driverActor ? DeployWorkflow(enriched, update = workflow.instances.nonEmpty) }
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule && workflow.instances.nonEmpty ⇒ driverActor ? UndeployWorkflow(workflow)
  }
}
