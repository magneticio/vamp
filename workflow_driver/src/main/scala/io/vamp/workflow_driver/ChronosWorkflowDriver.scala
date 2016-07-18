package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorRefFactory }
import io.vamp.common.akka.ActorRefFactoryExecutionContextProvider
import io.vamp.common.http.RestClient
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.workflow.TimeSchedule.RepeatCount
import io.vamp.model.workflow.{ DaemonSchedule, DefaultWorkflow, ScheduledWorkflow, TimeSchedule }
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled

import scala.concurrent.Future

class ChronosWorkflowDriver(url: String)(implicit override val actorRefFactory: ActorRefFactory) extends WorkflowDriver with ActorRefFactoryExecutionContextProvider {

  override def info: Future[Map[_, _]] = RestClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("chronos" -> Map("url" -> url))
  }

  override def request(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]): Unit = all() foreach { instances ⇒
    scheduledWorkflows.foreach { scheduled ⇒
      if (scheduled.schedule != DaemonSchedule)
        replyTo ! Scheduled(scheduled, instances.find(_.name == scheduled.name))
    }
  }

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case scheduledWorkflow if scheduledWorkflow.schedule != DaemonSchedule ⇒

      val workflow = scheduledWorkflow.workflow.asInstanceOf[DefaultWorkflow]
      val scale = scheduledWorkflow.scale.get.asInstanceOf[DefaultScale]

      val jobRequest = job(
        name = name(scheduledWorkflow),
        schedule = period(scheduledWorkflow),
        containerImage = workflow.containerImage.get,
        command = workflow.command.get,
        rootPath = WorkflowDriver.pathToString(scheduledWorkflow),
        cpu = scale.cpu.value,
        memory = scale.memory.value
      )

      RestClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case scheduledWorkflow if scheduledWorkflow.schedule != DaemonSchedule ⇒
      all() flatMap {
        case list ⇒ list.find(_.name == name(scheduledWorkflow)) match {
          case Some(_) ⇒ RestClient.delete(s"$url/scheduler/job/${name(scheduledWorkflow)}")
          case _       ⇒ Future.successful(false)
        }
      }
  }

  private def all(): Future[List[WorkflowInstance]] = RestClient.get[Any](s"$url/scheduler/jobs") map {
    case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("name", "")).filter(_.nonEmpty).map(WorkflowInstance)
    case _             ⇒ Nil
  }

  private def name(workflow: ScheduledWorkflow) = {
    if (workflow.name.matches("^[\\w\\s#_-]+$")) workflow.name else workflow.lookupName
  }

  private def period(workflow: ScheduledWorkflow) = workflow.schedule match {
    case TimeSchedule(period, RepeatCount(count), start) ⇒ s"R$count/${start.getOrElse("")}/${period.format}"
    case TimeSchedule(period, _, start) ⇒ s"R/${start.getOrElse("")}/${period.format}"
    case _ ⇒ "R1//PT1S"
  }

  private def job(name: String, schedule: String, containerImage: String, command: String, rootPath: String, cpu: Double, memory: Double) =
    s"""
       |{
       |  "name": "$name",
       |  "schedule": "$schedule",
       |  "container": {
       |    "type": "DOCKER",
       |    "image": "$containerImage",
       |    "network": ${WorkflowDriver.network},
       |    "volumes": []
       |  },
       |  "cpus": "$cpu",
       |  "mem": "$memory",
       |  "uris": [],
       |  "command": "$command",
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
