package io.vamp.workflow_driver

import akka.actor.ActorRef
import akka.pattern.ask
import io.vamp.common.ClassMapper
import io.vamp.common.akka.IoC
import io.vamp.container_driver.kubernetes.KubernetesDriverActor.{ CreateJob, DeleteJob }
import io.vamp.container_driver.kubernetes.{ Job, K8sClientConfig, KubernetesDriverActor }
import io.vamp.container_driver.{ ContainerDriverMapping, ContainerDriverValidation, DeployableType, DockerDeployableType }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.WorkflowValueResolver
import io.vamp.persistence.PersistenceActor
import io.vamp.pulse.Percolator.GetPercolator
import io.vamp.pulse.PulseActor

import scala.concurrent.Future

class KubernetesWorkflowActorMapper extends ClassMapper {
  val name = "kubernetesworkflow"
  val clazz: Class[_] = classOf[KubernetesWorkflowActor]
}

class KubernetesWorkflowActor extends DaemonWorkflowDriver with WorkflowValueResolver with ContainerDriverMapping with ContainerDriverValidation {

  override protected lazy val supportedDeployableTypes: List[DeployableType] = DockerDeployableType :: Nil

  override protected lazy val info: Future[Map[_, _]] = Future.successful(Map("kubernetes" → Map("url" → K8sClientConfig().url)))

  override protected lazy val driverActor: ActorRef = IoC.actorFor[KubernetesDriverActor]

  protected override def request: PartialFunction[Workflow, Unit] = ({
    case workflow if workflow.schedule.isInstanceOf[EventSchedule] ⇒
      IoC.actorFor[PulseActor] ? GetPercolator(WorkflowDriverActor.percolator(workflow)) map {
        case Some(_) if runnable(workflow) ⇒
          if (workflow.instances.isEmpty) {
            logger.info(s"KubernetesWorkflowActor workflow.instances.isEmpty : ${workflow.instances.isEmpty}")
            IoC.actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, Instance(workflow.name, "", Map(), deployed = true) :: Nil)
          }
        case _ ⇒
          if (workflow.instances.nonEmpty) {
            logger.info(s"KubernetesWorkflowActor workflow.instances.nonEmpty : ${workflow.instances.nonEmpty}")
            IoC.actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, Nil)
          }
      }
  }: PartialFunction[Workflow, Unit]) orElse super.request

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = super.schedule(data) orElse {
    case w if data.isInstanceOf[Event] && w.schedule.isInstanceOf[EventSchedule] ⇒ enrich(w, data).flatMap { workflow ⇒

      validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

      val name = s"workflow-${workflow.lookupName}-${data.asInstanceOf[Event].timestamp.toInstant.toEpochMilli}"
      val scale = workflow.scale.get.asInstanceOf[DefaultScale]

      driverActor ? CreateJob(Job(
        name = name,
        group = group(workflow),
        docker = docker(workflow),
        cpu = scale.cpu.value,
        mem = Math.round(scale.memory.value).toInt,
        environmentVariables = environment(workflow)
      ))
    }
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = super.unschedule() orElse {
    case w if w.schedule.isInstanceOf[EventSchedule] ⇒ driverActor ? DeleteJob(group(w))
  }

  override def resolverClasses: List[String] = super[WorkflowValueResolver].resolverClasses

  private def group(workflow: Workflow) = s"workflow-${workflow.lookupName}"
}
