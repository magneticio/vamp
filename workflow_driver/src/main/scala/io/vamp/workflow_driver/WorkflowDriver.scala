package io.vamp.workflow_driver

import akka.actor.ActorSystem
import io.vamp.common.Config
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.{ ContainerDriverActor, Docker }
import io.vamp.model.artifact.Workflow.Status
import io.vamp.model.artifact.Workflow.Status.RestartingPhase
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.WorkflowValueResolver
import io.vamp.persistence.{ ArtifactSupport, KeyValueStoreActor }
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.WorkflowDriverActor.{ GetScheduled, Schedule, Unschedule }
import io.vamp.workflow_driver.notification.WorkflowDriverNotificationProvider
import io.vamp.common.Id
import io.vamp.common.akka.IoC.actorFor
import io.vamp.persistence.refactor.exceptions.PersistenceTypeError
import akka.pattern.ask

import scala.concurrent.Future
import scala.language.postfixOps

object WorkflowDriver {

  val root = "workflows"

  val config = "vamp.workflow-driver"

  val workflowConfig = s"$config.workflow"

  val deployablesConfig = s"$workflowConfig.deployables"

  def path(workflow: Workflow) = root :: workflow.name :: Nil
}

trait WorkflowDriver extends ArtifactSupport with PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider with WorkflowValueResolver with VampJsonFormats {

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
    VampPersistence().read[Breed](Id[Breed](workflow.breed.name)).flatMap { breed ⇒
      val defaultBreed = if (breed.isInstanceOf[DefaultBreed]) breed.asInstanceOf[DefaultBreed]
      else throw PersistenceTypeError(Id[Breed](breed.name), breed.getClass.getName, "DefaultBreed")

      (deployables.get(defaultBreed.deployable.defaultType()) match {
        case Some(reference) ⇒ {
          VampPersistence().read[Breed](Id[Breed](reference)).map(x ⇒
            if (x.isInstanceOf[DefaultBreed]) x.asInstanceOf[DefaultBreed] else throw PersistenceTypeError(Id[Breed](x.name), x.getClass.getName, "DefaultBreed")
          )
        }
        case _ ⇒ Future.successful(defaultBreed)
      }).flatMap { executor ⇒

        val updatedEnvironmentVariables = (executor.environmentVariables ++ defaultBreed.environmentVariables ++ workflow.environmentVariables).
          map(env ⇒ env.name → resolveEnvironmentVariable(workflow, data)(env)).toMap.values.toList

        val updatedScale = workflow.scale.getOrElse(defaultScale).asInstanceOf[DefaultScale]
        val updatedNetwork = workflow.network.getOrElse(Docker.network())
        val updatedArguments = (defaultArguments ++ executor.arguments ++ defaultBreed.arguments ++ workflow.arguments).map(arg ⇒ arg.key → arg).toMap.values.toList
        val updatedHealthChecks = if (defaultBreed.healthChecks.isEmpty) executor.healthChecks else defaultBreed.healthChecks

        val workflowBreed = defaultBreed.copy(
          deployable = executor.deployable,
          ports = executor.ports,
          environmentVariables = updatedEnvironmentVariables,
          healthChecks = updatedHealthChecks
        )

        for {
          _ ← VampPersistence().update[Workflow](
            workflowSerilizationSpecifier.idExtractor(workflow),
            _.copy(environmentVariables = updatedEnvironmentVariables, scale = Some(updatedScale), network = Some(updatedNetwork),
              arguments = updatedArguments, healthChecks = updatedHealthChecks, breed = workflowBreed
            )
          )
          afterUpdateWorkflow ← VampPersistence().read[Workflow](workflowSerilizationSpecifier.idExtractor(workflow))
          _ ← actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(WorkflowDriver.path(workflow), Option(defaultBreed.deployable.definition))
        } yield afterUpdateWorkflow
      }
    }
  }

  protected def runnable(workflow: Workflow) = workflow.status match {
    case Status.Starting | Status.Running | Status.Restarting(Some(RestartingPhase.Starting)) ⇒ true
    case _ ⇒ false
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
