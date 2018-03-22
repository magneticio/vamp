package io.vamp.workflow_driver

import akka.actor.ActorSystem
import akka.pattern.ask
import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.{ ContainerDriverActor, Docker }
import io.vamp.model.artifact.Workflow.Status
import io.vamp.model.artifact.Workflow.Status.RestartingPhase
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.WorkflowValueResolver
import io.vamp.persistence.{ ArtifactSupport, KeyValueStoreActor, PersistenceActor }
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.WorkflowDriverActor.{ GetScheduled, Schedule, Unschedule }
import io.vamp.workflow_driver.notification.WorkflowDriverNotificationProvider

import scala.concurrent.Future
import scala.language.postfixOps

object WorkflowDriver {

  val root = "workflows"

  val config = "vamp.workflow-driver"

  val workflowConfig = s"$config.workflow"

  val deployablesConfig = s"$workflowConfig.deployables"

  def path(workflow: Workflow) = root :: workflow.name :: Nil
}

trait WorkflowDriver extends ArtifactSupport with PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider with WorkflowValueResolver {

  import WorkflowDriver._

  implicit def actorSystem: ActorSystem

  implicit val timeout = ContainerDriverActor.timeout()

  val defaultScale = DefaultScale(
    Quantity.of(Config.double(s"$workflowConfig.scale.cpu")()),
    MegaByte.of(Config.string(s"$workflowConfig.scale.memory")()),
    Config.int(s"$workflowConfig.scale.instances")()
  )

  def defaultArguments() = Config.stringList("vamp.operation.deployment.arguments")().map(Argument(_))

  val deployables: Map[String, String] = Config.list(deployablesConfig)().collect {
    case config: Map[_, _] ⇒ config.asInstanceOf[Map[String, String]]("type").trim → config.asInstanceOf[Map[String, String]]("breed").trim
  } toMap

  def receive = {
    case InfoRequest              ⇒ reply(info)
    case GetScheduled(workflows)  ⇒ request(workflows)
    case Schedule(workflow, data) ⇒ reply((schedule(data) orElse { case _ ⇒ Future.successful(false) }: PartialFunction[Workflow, Future[Any]])(workflow))
    case Unschedule(workflow)     ⇒ reply((unschedule() orElse { case _ ⇒ Future.successful(false) }: PartialFunction[Workflow, Future[Any]])(workflow))
  }

  protected def info: Future[Map[_, _]]

  protected def request(workflows: List[Workflow]): Unit

  protected def schedule(data: Any): PartialFunction[Workflow, Future[Any]]

  protected def unschedule(): PartialFunction[Workflow, Future[Any]]

  protected def enrich(workflow: Workflow, data: Any): Future[Workflow] = {
    artifactFor[DefaultBreed](workflow.breed, force = true).flatMap { breed ⇒
      (deployables.get(breed.deployable.defaultType()) match {
        case Some(reference) ⇒ artifactFor[DefaultBreed](reference)
        case _               ⇒ Future.successful(breed)
      }).flatMap { executor ⇒

        val environmentVariables = (executor.environmentVariables ++ breed.environmentVariables ++ workflow.environmentVariables).
          map(env ⇒ env.name → resolveEnvironmentVariable(workflow, data)(env)).toMap.values.toList

        val scale = workflow.scale.getOrElse(defaultScale).asInstanceOf[DefaultScale]
        val network = workflow.network.getOrElse(Docker.network())
        val arguments = (defaultArguments ++ executor.arguments ++ breed.arguments ++ workflow.arguments).map(arg ⇒ arg.key → arg).toMap.values.toList
        val healthChecks = if (breed.healthChecks.isEmpty) executor.healthChecks else breed.healthChecks

        val workflowBreed = breed.copy(
          deployable = executor.deployable,
          ports = executor.ports,
          environmentVariables = environmentVariables,
          healthChecks = healthChecks
        )

        for {
          _ ← actorFor[PersistenceActor] ? PersistenceActor.UpdateWorkflowEnvironmentVariables(workflow, environmentVariables)
          _ ← actorFor[PersistenceActor] ? PersistenceActor.UpdateWorkflowScale(workflow, scale)
          _ ← actorFor[PersistenceActor] ? PersistenceActor.UpdateWorkflowNetwork(workflow, network)
          _ ← actorFor[PersistenceActor] ? PersistenceActor.UpdateWorkflowArguments(workflow, arguments)
          _ ← actorFor[PersistenceActor] ? PersistenceActor.UpdateWorkflowBreed(workflow, workflowBreed)
          kv ← actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(WorkflowDriver.path(workflow))
          _ ← kv match {
            case Some(_) ⇒ Future.successful(kv)
            case _       ⇒ actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(WorkflowDriver.path(workflow), Option(breed.deployable.definition))
          }
        } yield workflow.copy(
          breed = workflowBreed,
          scale = Option(scale),
          arguments = arguments,
          network = Option(network),
          environmentVariables = environmentVariables
        )
      }
    }
  }

  protected def runnable(workflow: Workflow) = workflow.status match {
    case Status.Starting | Status.Running | Status.Restarting(Some(RestartingPhase.Starting)) ⇒ true
    case _ ⇒ false
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
