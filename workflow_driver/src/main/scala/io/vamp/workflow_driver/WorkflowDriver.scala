package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorSystem }
import io.vamp.common.akka.IoC._
import io.vamp.common.config.Config
import io.vamp.container_driver.DeployableType
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.workflow.Workflow
import io.vamp.persistence.db.PersistenceActor
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.persistence.operation.{ WorkflowArguments, WorkflowNetwork }

import scala.concurrent.Future

case class WorkflowInstance(name: String)

object WorkflowDeployable extends DeployableType("application/javascript")

object WorkflowDriver {

  val config = Config.config("vamp.workflow-driver")

  val vampUrl = config.string("vamp-url")

  def path(workflow: Workflow, script: Boolean) = "workflow" :: workflow.name :: (if (script) "workflow" :: Nil else Nil)

  def pathToString(workflow: Workflow) = KeyValueStoreActor.pathToString(path(workflow, script = false))
}

trait WorkflowDriver {

  import WorkflowDriver._

  implicit def actorSystem: ActorSystem

  val defaultDeployable = Deployable(config.string("workflow.deployable.type"), config.string("workflow.deployable.definition"))

  val additionalEnvironmentVariables: List[EnvironmentVariable] = config.stringList("workflow.environment-variables").map { env ⇒
    val index = env.indexOf('=')
    environmentVariable(env.substring(0, index), env.substring(index + 1))
  }

  val defaultScale = config.config("workflow.scale") match {
    case c ⇒ DefaultScale("", Quantity.of(c.double("cpu")), MegaByte.of(c.string("memory")), c.int("instances"))
  }

  val defaultArguments: List[Argument] = config.stringList("workflow.arguments").map(Argument(_))

  val defaultNetwork = config.string("workflow.network")

  val defaultCommand = config.string("workflow.command")

  def info: Future[Map[_, _]]

  def request(replyTo: ActorRef, scheduledWorkflows: List[Workflow]): Unit

  def schedule(data: Any): PartialFunction[Workflow, Future[Any]]

  def unschedule(): PartialFunction[Workflow, Future[Any]]

  protected def enrich: Workflow ⇒ Workflow = { workflow ⇒

    val breed = workflow.breed.asInstanceOf[DefaultBreed]

    val environmentVariables = (additionalEnvironmentVariables ++ List(environmentVariable("VAMP_URL", WorkflowDriver.vampUrl),
      environmentVariable("VAMP_KEY_VALUE_STORE_ROOT_PATH", WorkflowDriver.pathToString(workflow))) ++
      breed.environmentVariables ++ workflow.environmentVariables).map(env ⇒ env.name -> env.copy(interpolated = env.value)).toMap.values.toList

    val deployable = breed.deployable match {
      case d if WorkflowDeployable.matches(d) ⇒ defaultDeployable
      case d                                  ⇒ d
    }

    val network = workflow.network.getOrElse(defaultNetwork)
    actorFor[PersistenceActor] ! PersistenceActor.Update(WorkflowNetwork(workflow.name, network))

    val arguments = (defaultArguments ++ breed.arguments ++ workflow.arguments).map(arg ⇒ arg.key -> arg).toMap.values.toList
    actorFor[PersistenceActor] ! PersistenceActor.Update(WorkflowArguments(workflow.name, arguments))

    workflow.copy(
      breed = breed.copy(deployable = deployable, environmentVariables = environmentVariables),
      scale = Option(workflow.scale.getOrElse(defaultScale)),
      arguments = arguments,
      network = Option(network)
    )
  }

  private def environmentVariable(name: String, value: String) = EnvironmentVariable(name, None, Option(value), Option(value))
}
