package io.vamp.core.model.reader

import io.vamp.core.model.notification.UndefinedWorkflowTriggerError
import io.vamp.core.model.workflow._


object WorkflowReader extends YamlReader[Workflow] with ReferenceYamlReader[Workflow] {

  override def readReference(any: Any): Workflow = any match {
    case reference: String => WorkflowReference(reference)
    case map: collection.Map[_, _] =>
      implicit val source = map.asInstanceOf[YamlObject]
      if (<<?[Any]("script").isEmpty) WorkflowReference(name) else read(map.asInstanceOf[YamlObject])
  }

  override protected def expand(implicit source: YamlObject): YamlObject = {
    expandToList("import")
    source
  }

  override protected def parse(implicit source: YamlObject): Workflow = {
    DefaultWorkflow(name, <<?[List[String]]("import").getOrElse(Nil), <<![String]("script"))
  }
}

object ScheduledWorkflowReader extends YamlReader[ScheduledWorkflow] {

  override protected def expand(implicit source: YamlObject): YamlObject = {
    expandToList("tags")
    expandToList("import")
    source
  }

  override protected def parse(implicit source: YamlObject): ScheduledWorkflow = {
    val trigger = (<<?[String]("deployment"), <<?[String]("time"), <<?[List[String]]("tags")) match {

      case (Some(deployment), _, _) => DeploymentTrigger(deployment)

      case (None, Some(time), _) => TimeTrigger(time)

      case (None, None, Some(tags)) => EventTrigger(tags.toSet)

      case _ => error(UndefinedWorkflowTriggerError)
    }

    val workflow = <<?[Any]("workflow") match {
      case Some(w) => WorkflowReader.readReference(w)
      case _ => DefaultWorkflow("", <<?[List[String]]("import").getOrElse(Nil), <<![String]("script"))
    }

    ScheduledWorkflow(name, workflow, trigger, <<?[YamlObject]("storage").getOrElse(Map()).toMap)
  }
}
