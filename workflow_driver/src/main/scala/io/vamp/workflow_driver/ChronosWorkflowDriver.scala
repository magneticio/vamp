package io.vamp.workflow_driver

import io.vamp.common.http.RestClient
import io.vamp.model.workflow.TimeTrigger.RepeatTimesCount
import io.vamp.model.workflow.{ ScheduledWorkflow, TimeTrigger }

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

  override def schedule(workflow: ScheduledWorkflow, data: Any): Future[Any] = {
    RestClient.post[Any](s"$url/scheduler/iso8601", job(name(workflow), period(workflow)))
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

  private def job(name: String, schedule: String) =
    s"""
    |{
    |  "name": "$name",
    |  "schedule": "$schedule",
    |  "container": {
    |    "type": "DOCKER",
    |    "image": "magneticio/vamp-workflow-agent:0.9.0",
    |    "network": "BRIDGE",
    |    "volumes": []
    |  },
    |  "cpus": "0.1",
    |  "mem": "64",
    |  "uris": [],
    |  "command": ""
    |}
    |
  """.stripMargin
}
