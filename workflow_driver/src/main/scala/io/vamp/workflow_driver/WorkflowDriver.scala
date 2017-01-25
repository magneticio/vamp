package io.vamp.workflow_driver

import akka.actor.ActorSystem
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.common.config.Config
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.{ ContainerDriverActor, DeployableType, Docker }
import io.vamp.model.artifact.{ Deployable, _ }
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.TraitResolver
import io.vamp.persistence.{ ArtifactSupport, PersistenceActor }
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.workflow_driver.WorkflowDriverActor.{ GetScheduled, Schedule, Unschedule }
import io.vamp.workflow_driver.notification.WorkflowDriverNotificationProvider

import scala.concurrent.Future

object JavaScriptDeployableType extends DeployableType("application/javascript", "javascript") {
  val default = "application/javascript"
}

trait WorkflowDeployable {

  val javascriptConfig = s"${WorkflowDriver.config}.workflow.deployables.application.javascript"

  def matches(deployable: Deployable): Boolean = JavaScriptDeployableType.matches(deployable)

  def provide(deployable: Deployable): Deployable = {
    if (JavaScriptDeployableType.matches(deployable)) Deployable(
      Config.string(s"$javascriptConfig.type")(),
      Config.string(s"$javascriptConfig.definition")()
    )
    else deployable
  }

  def environmentVariables(deployable: Deployable): List[EnvironmentVariable] = {
    if (JavaScriptDeployableType.matches(deployable)) Config.stringList(s"$javascriptConfig.environment-variables")().map { env ⇒
      val index = env.indexOf('=')
      EnvironmentVariable(env.substring(0, index), None, Option(env.substring(index + 1)), None)
    }
    else Nil
  }

  def defaultArguments(deployable: Deployable): List[Argument] = {
    if (JavaScriptDeployableType.matches(deployable)) Config.stringList(s"$javascriptConfig.arguments")().map(Argument(_)) else Nil
  }

  def defaultNetwork(deployable: Deployable): String = {
    if (JavaScriptDeployableType.matches(deployable)) Config.string(s"$javascriptConfig.network")() else Docker.network()
  }

  def defaultCommand(deployable: Deployable): Option[String] = {
    if (JavaScriptDeployableType.matches(deployable)) Option(Config.string(s"$javascriptConfig.command")()) else None
  }
}

object WorkflowDriver {

  val root = "workflows"

  val config = "vamp.workflow-driver"

  def path(workflow: Workflow) = root :: workflow.name :: Nil
}

trait WorkflowDriver extends WorkflowDeployable with ArtifactSupport with PulseFailureNotifier with CommonSupportForActors with WorkflowDriverNotificationProvider with TraitResolver {

  import WorkflowDriver._

  implicit def actorSystem: ActorSystem

  implicit val timeout = ContainerDriverActor.timeout()

  val defaultScale = DefaultScale(
    Quantity.of(Config.double(s"$config.workflow.scale.cpu")()),
    MegaByte.of(Config.string(s"$config.workflow.scale.memory")()),
    Config.int(s"$config.workflow.scale.instances")()
  )

  def receive = {
    case InfoRequest              ⇒ reply(info)
    case GetScheduled(workflows)  ⇒ request(workflows)
    case Schedule(workflow, data) ⇒ reply(schedule(data)(workflow))
    case Unschedule(workflow)     ⇒ reply(unschedule()(workflow))
  }

  protected def info: Future[Map[_, _]]

  protected def request(workflows: List[Workflow]): Unit

  protected def schedule(data: Any): PartialFunction[Workflow, Future[Any]]

  protected def unschedule(): PartialFunction[Workflow, Future[Any]]

  protected def enrich: Workflow ⇒ Workflow = { workflow ⇒

    val breed = workflow.breed.asInstanceOf[DefaultBreed]

    val environmentVariables = {
      resolveGlobalEnvironmentVariables(workflow, breed.deployable) ++ breed.environmentVariables ++ workflow.environmentVariables
    }.map(env ⇒ env.name → env.copy(interpolated = env.value)).toMap.values.toList

    actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowEnvironmentVariables(workflow, environmentVariables)

    val scale = workflow.scale.getOrElse(defaultScale).asInstanceOf[DefaultScale]
    actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowScale(workflow, scale)

    val network = workflow.network.getOrElse(defaultNetwork(breed.deployable))
    actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowNetwork(workflow, network)

    val arguments = (defaultArguments(breed.deployable) ++ breed.arguments ++ workflow.arguments).map(arg ⇒ arg.key → arg).toMap.values.toList
    actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowArguments(workflow, arguments)

    workflow.copy(
      breed = breed.copy(
        deployable = provide(breed.deployable),
        environmentVariables = environmentVariables
      ),
      scale = Option(scale),
      arguments = arguments,
      network = Option(network),
      environmentVariables = environmentVariables
    )
  }

  private def resolveGlobalEnvironmentVariables(workflow: Workflow, deployable: Deployable): List[EnvironmentVariable] = {
    def interpolated: ValueReference ⇒ String = {
      case ref: LocalReference if ref.name == "workflow" ⇒ workflow.name
      case _ ⇒ ""
    }
    environmentVariables(deployable).map { env ⇒
      val value = resolve(env.value.getOrElse(""), interpolated)
      env.copy(value = Option(value), interpolated = Option(value))
    }
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
