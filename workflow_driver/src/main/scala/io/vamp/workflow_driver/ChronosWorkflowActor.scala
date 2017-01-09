package io.vamp.workflow_driver

import io.vamp.common.config.Config
import io.vamp.common.http.HttpClient
import io.vamp.common.spi.ClassMapper
import io.vamp.container_driver._
import io.vamp.model.artifact.TimeSchedule.RepeatCount
import io.vamp.model.artifact._

import scala.concurrent.Future

class ChronosWorkflowActorMapper extends ClassMapper {
  val name = "chronos"
  val clazz = classOf[ChronosWorkflowActor]
}

class ChronosWorkflowActor extends WorkflowDriver with ContainerDriverValidation {

  private val url = Config.string("vamp.workflow-driver.chronos.url")()

  private val httpClient = new HttpClient

  override protected def supportedDeployableTypes = DockerDeployable :: Nil

  override def receive = super.receive orElse {
    case _ ⇒
  }

  protected override def info: Future[Map[_, _]] = httpClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("chronos" → Map("url" → url))
  }

  protected override def request(workflows: List[Workflow]): Unit = {
    // TODO update workflow instances
    //    val replyTo = sender()
    //    all() foreach { instances ⇒
    //      workflows.foreach { workflow ⇒
    //        if (workflow.schedule != DaemonSchedule)
    //          replyTo ! Scheduled(workflow, instances.find(_.name == workflow.name))
    //      }
    //    }
  }

  protected override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case w if w.schedule != DaemonSchedule ⇒

      val workflow = enrich(w)
      val breed = workflow.breed.asInstanceOf[DefaultBreed]

      validateDeployable(workflow.breed.asInstanceOf[DefaultBreed].deployable)

      val jobRequest = job(
        name = name(workflow),
        schedule = period(workflow),
        containerImage = breed.deployable.definition,
        environmentVariables = breed.environmentVariables,
        scale = workflow.scale.get.asInstanceOf[DefaultScale],
        network = workflow.network.getOrElse(Docker.network())
      )

      httpClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
    case _ ⇒ Future.successful(false)
  }

  protected override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule != DaemonSchedule ⇒
      all() flatMap {
        list ⇒
          list.find(_.name == name(workflow)) match {
            case Some(_) ⇒ httpClient.delete(s"$url/scheduler/job/${name(workflow)}")
            case _       ⇒ Future.successful(false)
          }
      }
    case _ ⇒ Future.successful(false)
  }

  private def all(): Future[List[WorkflowInstance]] = httpClient.get[Any](s"$url/scheduler/jobs") map {
    case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("name", "")).filter(_.nonEmpty).map(WorkflowInstance)
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

  private def job(name: String, schedule: String, containerImage: String, environmentVariables: List[EnvironmentVariable], scale: DefaultScale, network: String) = {

    val vars = environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) → ev.interpolated.getOrElse("")).map {
      case (n, v) ⇒ s"""{ "name": "$n", "value": "$v" }"""
    } mkString ","

    s"""
       |{
       |  "name": "$name",
       |  "schedule": "$schedule",
       |  "container": {
       |    "type": "DOCKER",
       |    "image": "$containerImage",
       |    "network": "$network",
       |    "volumes": []
       |  },
       |  "cpus": "${scale.cpu.value}",
       |  "mem": "${scale.memory.value}",
       |  "uris": [],
       |  "environmentVariables": [ $vars ],
       |  "command": "$defaultCommand"
       |}
  """.stripMargin
  }
}
