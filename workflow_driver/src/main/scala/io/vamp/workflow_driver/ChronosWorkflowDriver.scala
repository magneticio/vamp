package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorRefFactory, ActorSystem }
import io.vamp.common.akka.ActorRefFactoryExecutionContextProvider
import io.vamp.common.http.HttpClient
import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.workflow.TimeSchedule.RepeatCount
import io.vamp.model.workflow.{ DaemonSchedule, TimeSchedule, Workflow }
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled
import io.vamp.workflow_driver.notification.WorkflowDriverNotificationProvider

import scala.concurrent.Future

class ChronosWorkflowDriver(url: String)(implicit override val actorSystem: ActorSystem) extends WorkflowDriver with ContainerDriverValidation with ActorRefFactoryExecutionContextProvider with WorkflowDriverNotificationProvider {

  implicit def actorRefFactory: ActorRefFactory = actorSystem

  private val httpClient = new HttpClient

  override protected def supportedDeployableTypes = DockerDeployable :: Nil

  override def info: Future[Map[_, _]] = httpClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("chronos" -> Map("url" -> url))
  }

  override def request(replyTo: ActorRef, workflows: List[Workflow]): Unit = all() foreach { instances ⇒
    workflows.foreach { workflow ⇒
      if (workflow.schedule != DaemonSchedule)
        replyTo ! Scheduled(workflow, instances.find(_.name == workflow.name))
    }
  }

  override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
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
        network = workflow.network.getOrElse(Docker.network)
      )

      httpClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
  }

  override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule != DaemonSchedule ⇒
      all() flatMap {
        list ⇒
          list.find(_.name == name(workflow)) match {
            case Some(_) ⇒ httpClient.delete(s"$url/scheduler/job/${name(workflow)}")
            case _       ⇒ Future.successful(false)
          }
      }
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

    val vars = environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) -> ev.interpolated.getOrElse("")).map {
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
