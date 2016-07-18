package io.vamp.model.reader

import java.time.{ OffsetDateTime, ZoneId }
import java.util.Date

import io.vamp.model.artifact.DefaultScale
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.workflow.TimeSchedule.{ RepeatCount, RepeatForever }
import io.vamp.model.workflow._

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
    val trigger = timeTrigger getOrElse {
      eventTrigger getOrElse {
        daemonTrigger getOrElse throwException(UndefinedWorkflowTriggerError)
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

  private def timeTrigger(implicit source: YamlSourceReader): Option[Schedule] = {
    <<?[String]("period") map { period ⇒

      val repeat = <<?[Int]("repeat") match {
        case None        ⇒ RepeatForever
        case Some(count) ⇒ RepeatCount(count)
      }

      val start = <<?[Date]("start") map (time ⇒ OffsetDateTime.from(time.toInstant.atZone(ZoneId.of("UTC"))))

      Try(TimeSchedule(period, repeat, start)).getOrElse(throwException(IllegalPeriod(period)))
    }
  }

  private def eventTrigger(implicit source: YamlSourceReader): Option[Schedule] = {
    <<?[List[String]]("tags").map(tags ⇒ EventSchedule(tags.toSet))
  }

  private def daemonTrigger(implicit source: YamlSourceReader): Option[Schedule] = {
    <<?[Boolean]("daemon") match {
      case Some(daemon) if daemon ⇒ Option(DaemonSchedule)
      case _                      ⇒ None
    }
  }
}
