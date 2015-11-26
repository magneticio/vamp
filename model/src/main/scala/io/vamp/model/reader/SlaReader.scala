package io.vamp.model.reader

import io.vamp.model.artifact._

import scala.concurrent.duration._
import scala.language.postfixOps

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {
  import YamlSourceReader._

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    <<?[Any]("escalations") match {
      case None                ⇒
      case Some(list: List[_]) ⇒ if (list.size > 1) >>("escalations", List(YamlSourceReader("type" -> "to_all", "escalations" -> list)))
      case Some(any)           ⇒ >>("escalations", List(any))
    }
    source
  }

  override protected def validateEitherReferenceOrAnonymous(implicit source: YamlSourceReader): YamlSourceReader = {
    if (source.pull(k ⇒ k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlSourceReader): Sla = SlaReference(reference, escalations)

  override protected def createDefault(implicit source: YamlSourceReader): Sla = `type` match {
    case "response_time_sliding_window" ⇒

      val upper = <<![Int]("threshold" :: "upper") milliseconds
      val lower = <<![Int]("threshold" :: "lower") milliseconds
      val interval = <<![Int]("window" :: "interval") seconds
      val cooldown = <<![Int]("window" :: "cooldown") seconds

      ResponseTimeSlidingWindowSla(name, upper, lower, interval, cooldown, escalations)

    case "escalation_only" ⇒
      EscalationOnlySla(name, escalations)

    case generic ⇒
      GenericSla(name, generic, escalations, parameters)
  }

  protected def escalations(implicit source: YamlSourceReader): List[Escalation] = <<?[YamlList]("escalations") match {
    case None ⇒ List[Escalation]()
    case Some(list: YamlList) ⇒ list.map {
      EscalationReader.readReferenceOrAnonymous
    }
  }

  override protected def parameters(implicit source: YamlSourceReader): Map[String, Any] = source.flatten(key ⇒ key != "name" && key != "type" && key != "escalations")
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {
  import YamlSourceReader._

  override protected def expand(implicit source: YamlSourceReader): YamlSourceReader = {
    if (!isReference && source.size == 1) source.pull().head match {
      case (key, value) ⇒
        key match {
          case "to_all" | "to_one" ⇒
            >>("type", key)
            source.set(key, None)
            value match {
              case l: List[_] ⇒ >>("escalations", value)
              case _          ⇒ source.push(value.asInstanceOf[YamlSourceReader])
            }
          case _ ⇒
        }
    }
    source
  }

  override protected def createReference(implicit source: YamlSourceReader): Escalation = EscalationReference(reference)

  override protected def createDefault(implicit source: YamlSourceReader): Escalation = `type` match {
    case "to_all" ⇒
      ToAllEscalation(name, <<![YamlList]("escalations").map(EscalationReader.readReferenceOrAnonymous))

    case "to_one" ⇒
      ToOneEscalation(name, <<![YamlList]("escalations").map(EscalationReader.readReferenceOrAnonymous))

    case "scale_instances" ⇒
      ScaleInstancesEscalation(name, <<![Int]("minimum"), <<![Int]("maximum"), <<![Int]("scale_by"), <<?[String]("target"))

    case "scale_cpu" ⇒
      ScaleCpuEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"), <<?[String]("target"))

    case "scale_memory" ⇒
      ScaleMemoryEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"), <<?[String]("target"))

    case generic ⇒
      GenericEscalation(name, generic, parameters)
  }
}
