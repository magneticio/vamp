package io.vamp.model.reader

import io.vamp.model.notification.UndefinedWorkflowTriggerError
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.workflow._

object WorkflowReader extends YamlReader[Workflow] with ReferenceYamlReader[Workflow] {

  override def readReference(any: Any): Workflow = any match {
    case reference: String ⇒ WorkflowReference(reference)
    case yaml: YamlSourceReader ⇒
      implicit val source = yaml
      if (<<?[Any]("script").isEmpty) WorkflowReference(name) else read(source)
  }

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    expandToList("import")
    expandToList("requires")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): Workflow = {
    DefaultWorkflow(name, <<?[List[String]]("import").getOrElse(Nil), <<?[List[String]]("requires").getOrElse(Nil), <<![String]("script"))
  }
}

object ScheduledWorkflowReader extends YamlReader[ScheduledWorkflow] {

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    expandToList("tags")
    expandToList("import")
    source
  }

  override protected def parse(implicit source: YamlSourceReader): ScheduledWorkflow = {
    val trigger = (<<?[String]("deployment"), <<?[String]("time"), <<?[List[String]]("tags")) match {

      case (Some(deployment), _, _) ⇒ DeploymentTrigger(deployment)

      case (None, Some(time), _)    ⇒ TimeTrigger(time)

      case (None, None, Some(tags)) ⇒ EventTrigger(tags.toSet)

      case _                        ⇒ throwException(UndefinedWorkflowTriggerError)
    }

    val workflow = <<?[Any]("workflow") match {
      case Some(w) ⇒ WorkflowReader.readReference(w)
      case _       ⇒ DefaultWorkflow("", <<?[List[String]]("import").getOrElse(Nil), Nil, <<![String]("script"))
    }

    ScheduledWorkflow(name, workflow, trigger, <<?[YamlSourceReader]("storage").getOrElse(YamlSourceReader()).pull())
  }
}
