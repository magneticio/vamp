package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorRefFactory, ActorSystem }
import io.vamp.common.akka.ActorRefFactoryExecutionContextProvider
import io.vamp.common.http.RestClient
import io.vamp.model.artifact.{ DefaultBreed, DefaultScale }
import io.vamp.model.workflow.TimeSchedule.RepeatCount
import io.vamp.model.workflow.{ DaemonSchedule, TimeSchedule, Workflow }
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled

import scala.concurrent.Future

class ChronosWorkflowDriver(url: String)(implicit override val actorSystem: ActorSystem) extends WorkflowDriver with ActorRefFactoryExecutionContextProvider {

  implicit def actorRefFactory: ActorRefFactory = actorSystem

  override def info: Future[Map[_, _]] = RestClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("chronos" -> Map("url" -> url))
  }

  override def request(replyTo: ActorRef, workflows: List[Workflow]): Unit = all() foreach { instances ⇒
    workflows.foreach { workflow ⇒
      if (workflow.schedule != DaemonSchedule)
        replyTo ! Scheduled(workflow, instances.find(_.name == workflow.name))
    }
  }

  override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule != DaemonSchedule ⇒

      val scale = workflow.scale.get.asInstanceOf[DefaultScale]

      val jobRequest = job(
        name = name(workflow),
        schedule = period(workflow),
        containerImage = workflow.breed.asInstanceOf[DefaultBreed].deployable.definition,
        rootPath = WorkflowDriver.pathToString(workflow),
        cpu = scale.cpu.value,
        memory = scale.memory.value
      )

      RestClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
  }

  override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule != DaemonSchedule ⇒
      all() flatMap {
        list ⇒
          list.find(_.name == name(workflow)) match {
            case Some(_) ⇒ RestClient.delete(s"$url/scheduler/job/${name(workflow)}")
            case _       ⇒ Future.successful(false)
          }
      }
  }

  private def all(): Future[List[WorkflowInstance]] = RestClient.get[Any](s"$url/scheduler/jobs") map {
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

  private def job(name: String, schedule: String, containerImage: String, rootPath: String, cpu: Double, memory: Double) =
    s"""
       |{
       |  "name": "$name",
       |  "schedule": "$schedule",
       |  "container": {
       |    "type": "DOCKER",
       |    "image": "$containerImage",
       |    "network": "BRIDGE",
       |    "volumes": []
       |  },
       |  "cpus": "$cpu",
       |  "mem": "$memory",
       |  "uris": [],
       |  "environmentVariables": [
       |    {
       |      "name": "VAMP_URL",
       |      "value": "${WorkflowDriver.vampUrl}"
       |    },
       |    {
       |      "name": "VAMP_KEY_VALUE_STORE_ROOT_PATH",
       |      "value": "$rootPath"
       |    }
       |  ]
       |}
  """.stripMargin
}
