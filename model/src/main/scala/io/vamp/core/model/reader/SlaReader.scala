package io.vamp.core.model.reader

import io.vamp.core.model.artifact._

import scala.concurrent.duration._
import scala.language.postfixOps

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

  override protected def expand(implicit source: YamlObject): YamlObject = {
    <<?[Any]("escalations") match {
      case None =>
      case Some(list: List[_]) => if (list.size > 1) >>("escalations", List(new YamlObject() += ("type" -> "to_all") += ("escalations" -> list)))
      case Some(any) => >>("escalations", List(any))
    }
    source
  }

  override protected def validateEitherReferenceOrAnonymous(implicit source: YamlObject): YamlObject = {
    if (source.filterKeys(k => k != "name" && k != "escalations").nonEmpty) super.validate
    source
  }

  override protected def createReference(implicit source: YamlObject): Sla = SlaReference(reference, escalations)

  override protected def createDefault(implicit source: YamlObject): Sla = `type` match {
    case "response_time_sliding_window" =>

      val upper = <<![Int]("threshold" :: "upper") milliseconds
      val lower = <<![Int]("threshold" :: "lower") milliseconds
      val interval = <<![Int]("window" :: "interval") seconds
      val cooldown = <<![Int]("window" :: "cooldown") seconds

      ResponseTimeSlidingWindowSla(name, upper, lower, interval, cooldown, escalations)

    case "escalation_only" =>
      EscalationOnlySla(name, escalations)

    case generic =>
      GenericSla(name, generic, escalations, parameters)
  }

  protected def escalations(implicit source: YamlObject): List[Escalation] = <<?[YamlList]("escalations") match {
    case None => List[Escalation]()
    case Some(list: YamlList) => list.map {
      EscalationReader.readReferenceOrAnonymous
    }
  }

  override protected def parameters(implicit source: YamlObject): Map[String, Any] = super.parameters.filterKeys(_ != "escalations")
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {

  override protected def expand(implicit source: YamlObject): YamlObject = {
    if (!isReference && source.size == 1) source.head match {
      case (key, value) =>
        key match {
          case "to_all" | "to_one" =>
            >>("type", key)
            source -= key
            value match {
              case l: List[_] => >>("escalations", value)
              case _ => source ++= value.asInstanceOf[YamlObject]
            }
          case _ =>
        }
    }
    source
  }

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference)

  override protected def createDefault(implicit source: YamlObject): Escalation = `type` match {
    case "to_all" =>
      ToAllEscalation(name, <<![YamlList]("escalations").map(EscalationReader.readReferenceOrAnonymous))

    case "to_one" =>
      ToOneEscalation(name, <<![YamlList]("escalations").map(EscalationReader.readReferenceOrAnonymous))

    case "scale_instances" =>
      ScaleInstancesEscalation(name, <<![Int]("minimum"), <<![Int]("maximum"), <<![Int]("scale_by"), <<?[String]("target"))

    case "scale_cpu" =>
      ScaleCpuEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"), <<?[String]("target"))

    case "scale_memory" =>
      ScaleMemoryEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"), <<?[String]("target"))

    case generic =>
      GenericEscalation(name, generic, parameters)
  }
}
