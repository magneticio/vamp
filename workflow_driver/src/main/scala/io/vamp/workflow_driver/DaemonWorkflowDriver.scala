package io.vamp.workflow_driver

import akka.actor.{ Actor, ActorRef }
import akka.pattern.ask
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.akka.IoC.actorFor
import io.vamp.container_driver.ContainerDriverActor.{ DeployWorkflow, GetWorkflow, UndeployWorkflow }
import io.vamp.container_driver.ContainerWorkflow
import io.vamp.model.artifact.{ DaemonSchedule, DefaultBreed, Instance, Workflow }
import io.vamp.persistence.PersistenceActor

import scala.concurrent.Future

trait DaemonWorkflowDriver extends WorkflowDriver with LazyLogging {

  protected def driverActor: ActorRef

  override def receive: Actor.Receive = super.receive orElse {
    case ContainerWorkflow(workflow, containers, health) ⇒

      logger.info("DaemonWorkflowDriver - received ContainerWorkflow {}", workflow.name)

      if (workflow.health != health) actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowHealth(workflow, health)

      val instances = containers.map(_.instances.map { instance ⇒
        val ports: Map[String, Int] = {
          workflow.breed match {
            case breed: DefaultBreed ⇒ breed.ports.map(_.name) zip instance.ports
            case _                   ⇒ Map[String, Int]()
          }
        }.toMap

        logger.info("DaemonWorkflowDriver - Ports for ContainerInstance {} are {}", instance.toString, ports.toString)

        Instance(instance.name, instance.host, ports, instance.deployed)
      }).getOrElse(Nil)

      if (workflow.instances != instances) actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, instances)

    case _ ⇒ logger.info("DaemonWorkflowDriver - received an unrecognised message")
  }

  protected override def request(workflows: List[Workflow]): Unit = workflows.foreach(request)

  protected def request: PartialFunction[Workflow, Unit] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒ driverActor ! GetWorkflow(workflow, self)
    case workflow                                        ⇒ logger.info("DaemonWorkflowDriver - Workflow schedule is {} instead of DaemonSchedule", workflow.schedule)
  }

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒ {
      logger.info("DaemonWorkflowDriver - Workflow number of instances is {}", workflow.instances.size)
      enrich(workflow, data).flatMap { enriched ⇒ driverActor ? DeployWorkflow(enriched, update = workflow.instances.nonEmpty) }
    }
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule && workflow.instances.nonEmpty ⇒ driverActor ? UndeployWorkflow(workflow)
  }
}
