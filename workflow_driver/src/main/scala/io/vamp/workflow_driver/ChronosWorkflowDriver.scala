package io.vamp.workflow_driver

import io.vamp.common.http.RestClient
import io.vamp.model.artifact.DefaultScale
import io.vamp.model.workflow.TimeTrigger.RepeatTimesCount
import io.vamp.model.workflow.{ DefaultWorkflow, ScheduledWorkflow, TimeTrigger }

import scala.concurrent.{ ExecutionContext, Future }

class ChronosWorkflowDriver(ec: ExecutionContext, url: String) extends WorkflowDriver {
  private implicit val executionContext = ec

  override def info: Future[Any] = RestClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("type" -> "chronos", "url" -> url)
  }

  override def all(): Future[List[WorkflowInstance]] = RestClient.get[Any](s"$url/scheduler/jobs") map {
    case list: List[_] ⇒ list.map(_.asInstanceOf[Map[String, String]].getOrElse("name", "")).filter(_.nonEmpty).map(WorkflowInstance)
    case _             ⇒ Nil
  }

  override def schedule(scheduledWorkflow: ScheduledWorkflow, data: Any): Future[Any] = {

    val workflow = scheduledWorkflow.workflow.asInstanceOf[DefaultWorkflow]
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]

    val jobRequest = job(
      name = name(scheduledWorkflow),
      schedule = period(scheduledWorkflow),
      containerImage = workflow.containerImage.get,
      command = workflow.command.get,
      rootPath = WorkflowDriver.pathToString(scheduledWorkflow),
      cpu = scale.cpu,
      memory = scale.memory.value
    )

    RestClient.post[Any](s"$url/scheduler/iso8601", jobRequest)
  }

  override def unschedule(workflow: ScheduledWorkflow): Future[Any] = all() flatMap {
    case list ⇒ list.find(_.name == name(workflow)) match {
      case Some(_) ⇒ RestClient.delete(s"$url/scheduler/job/${name(workflow)}")
      case _       ⇒ Future.successful(false)
    }
  }

  private def name(workflow: ScheduledWorkflow) = {
    if (workflow.name.matches("^[\\w\\s#_-]+$")) workflow.name else workflow.lookupName
  }

  private def period(workflow: ScheduledWorkflow) = workflow.trigger match {
    case TimeTrigger(period, RepeatTimesCount(count), start) ⇒ s"R$count/${start.getOrElse("")}/${period.format}"
    case TimeTrigger(period, _, start) ⇒ s"R/${start.getOrElse("")}/${period.format}"
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
    |    "network": "BRIDGE",
    |    "volumes": []
    |  },
    |  "cpus": "$cpu",
    |  "mem": "$memory",
    |  "uris": [],
    |  "command": "$command",
    |  "environmentVariables": [
    |    {
    |      "name": "VAMP_KEY_VALUE_STORE_ROOT_PATH",
    |      "value": "$rootPath"
    |    }
    |  ]
    |}
  """.stripMargin
}
