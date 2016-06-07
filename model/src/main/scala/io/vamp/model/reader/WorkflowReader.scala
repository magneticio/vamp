package io.vamp.model.reader

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import io.vamp.model.artifact.DefaultScale
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.workflow.TimeTrigger.{ RepeatForever, RepeatTimesCount }
import io.vamp.model.workflow.{ DaemonTrigger, _ }

import scala.util.Try

object WorkflowReader extends YamlReader[Workflow] with ReferenceYamlReader[Workflow] {

  override def readReference: PartialFunction[Any, Workflow] = {
    case reference: String ⇒ WorkflowReference(reference)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (yaml.size == 1) WorkflowReference(name) else read(source)
  }

  override protected def parse(implicit source: YamlSourceReader): Workflow = {
    DefaultWorkflow(name, <<?[String]("container-image"), <<?[String]("script"), <<?[String]("command"))
  }

  override def validate(workflow: Workflow): Workflow = workflow match {
    case DefaultWorkflow(_, None, None, None) ⇒ throwException(NoWorkflowRunnable(workflow.name))
    case _                                    ⇒ workflow
  }
}

object ScheduledWorkflowReader extends YamlReader[ScheduledWorkflow] {

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    expandToList("tags")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): ScheduledWorkflow = {
    val trigger = deploymentTrigger getOrElse {
      timeTrigger getOrElse {
        eventTrigger getOrElse {
          daemonTrigger getOrElse throwException(UndefinedWorkflowTriggerError)
        }
      }
    }

    val workflow = <<?[Any]("workflow") match {
      case Some(w) if <<?[String]("script").isDefined ⇒ throwException(BothWorkflowAndScriptError)
      case Some(w) ⇒ WorkflowReader.readReference(w)
      case _ ⇒ DefaultWorkflow("", None, Option(<<![String]("script")), None)
    }

    ScheduledWorkflow(name, workflow, trigger, ScaleReader.readOptionalReferenceOrAnonymous("scale"))
  }

  override def validate(workflow: ScheduledWorkflow): ScheduledWorkflow = workflow.scale match {
    case Some(scale: DefaultScale) if scale.instances > 1 ⇒ throwException(InvalidScheduledWorkflowScale(scale))
    case _ ⇒ workflow
  }

  private def deploymentTrigger(implicit source: YamlSourceReader): Option[Trigger] = {
    <<?[String]("deployment").map {
      case deployment ⇒ DeploymentTrigger(deployment)
    }
  }

  private def timeTrigger(implicit source: YamlSourceReader): Option[Trigger] = {
    <<?[String]("period") map { period ⇒

      val repeatTimes = <<?[Int]("repeatCount") match {
        case None        ⇒ RepeatForever
        case Some(count) ⇒ RepeatTimesCount(count)
      }

      val startTime = <<?[Date]("startTime") map (time ⇒ OffsetDateTime.from(time.toInstant.atZone(ZoneId.of("UTC"))))

      Try(TimeTrigger(period, repeatTimes, startTime)).getOrElse(throwException(IllegalPeriod(period)))
    }
  }

  private def eventTrigger(implicit source: YamlSourceReader): Option[Trigger] = {
    <<?[List[String]]("tags").map {
      case tags ⇒ EventTrigger(tags.toSet)
    }
  }

  private def daemonTrigger(implicit source: YamlSourceReader): Option[Trigger] = {
    <<?[Boolean]("daemon") match {
      case Some(daemon) if daemon ⇒ Option(DaemonTrigger)
      case _                      ⇒ None
    }
  }
}
