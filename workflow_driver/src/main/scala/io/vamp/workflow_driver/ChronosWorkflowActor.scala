package io.vamp.workflow_driver

import io.vamp.common.akka.IoC
import io.vamp.common.config.Config
import io.vamp.common.http.HttpClient
import io.vamp.common.spi.ClassMapper
import io.vamp.container_driver._
import io.vamp.model.artifact.TimeSchedule.RepeatCount
import io.vamp.model.artifact._
import io.vamp.persistence.db.PersistenceActor

import scala.concurrent.Future

class ChronosWorkflowActorMapper extends ClassMapper {
  val name = "chronos"
  val clazz = classOf[ChronosWorkflowActor]
}

class ChronosWorkflowActor extends WorkflowDriver with ContainerDriverValidation {

  private val url = Config.string("vamp.workflow-driver.chronos.url")()

  private val httpClient = new HttpClient

  override protected def supportedDeployableTypes = DockerDeployableType :: Nil

  override def receive = super.receive orElse {
    case _ ⇒
  }

  protected override def info: Future[Map[_, _]] = httpClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("chronos" → Map("url" → url))
  }

  protected override def request(workflows: List[Workflow]): Unit = all() foreach { instances ⇒
    workflows.foreach {
      case workflow if workflow.schedule != DaemonSchedule ⇒
        val container = if (instances.contains(workflow.name)) Instance(workflow.name, "", Map(), deployed = true) :: Nil else Nil
        IoC.actorFor[PersistenceActor] ! PersistenceActor.UpdateWorkflowInstances(workflow, container)
      case _ ⇒
    }
  }

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case w if w.schedule != DaemonSchedule ⇒

      val command = defaultCommand(w.breed.asInstanceOf[DefaultBreed].deployable).getOrElse("")
      val workflow = enrich(w)
      val breed = workflow.breed.asInstanceOf[DefaultBreed]

      validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

      val jobRequest = job(
        name = name(workflow),
        schedule = period(workflow),
        image = breed.deployable.definition,
        environmentVariables = breed.environmentVariables,
        scale = workflow.scale.get.asInstanceOf[DefaultScale],
        network = workflow.network.getOrElse(Docker.network()),
        command = command
      )

      httpClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
    case _ ⇒ Future.successful(false)
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule != DaemonSchedule ⇒
      all() flatMap {
        list ⇒ if (list.contains(name(workflow))) httpClient.delete(s"$url/scheduler/job/${name(workflow)}") else Future.successful(false)
      }
    case _ ⇒ Future.successful(false)
  }

  private def all(): Future[List[String]] = httpClient.get[Any](s"$url/scheduler/jobs") map {
    case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("name", "")).filter(_.nonEmpty)
    case _             ⇒ Nil
  }

  private def name(workflow: Workflow) = {
    if (workflow.name.matches("^[\\w\\s#_-]+$")) workflow.name else workflow.lookupName
  }

  private def period(workflow: Workflow) = workflow.schedule match {
    case TimeSchedule(period, RepeatCount(count), start) ⇒ s"R$count/${start.getOrElse("")}/${period.format}"
    case TimeSchedule(period, _, start) ⇒ s"R/${start.getOrElse("")}/${period.format}"
    case _ ⇒ "R1//PT1S"
  }

  private def job(name: String, schedule: String, image: String, environmentVariables: List[EnvironmentVariable], scale: DefaultScale, network: String, command: String) = {
    val vars = environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) → ev.interpolated.getOrElse("")).map {
      case (n, v) ⇒ s"""{ "name": "$n", "value": "$v" }"""
    } mkString ","
    s"""
       |{
       |  "name": "$name",
       |  "schedule": "$schedule",
       |  "container": {
       |    "type": "DOCKER",
       |    "image": "$image",
       |    "network": "$network",
       |    "volumes": []
       |  },
       |  "cpus": "${scale.cpu.value}",
       |  "mem": "${scale.memory.value}",
       |  "uris": [],
       |  "environmentVariables": [ $vars ],
       |  "command": "$command"
       |}
  """.stripMargin
  }
}
