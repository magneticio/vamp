package io.magnetic.vamp_core.model.reader

import io.magnetic.vamp_core.model.artifact._

import scala.concurrent.duration._
import scala.language.postfixOps

object SlaReader extends YamlReader[Sla] with WeakReferenceYamlReader[Sla] {

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

  override protected def isReference(implicit source: YamlObject): Boolean = {
    <<?[String]("name").nonEmpty && (source.size == 1 || source.size == 2 && <<?[String]("escalations").nonEmpty)
  }
}

object EscalationReader extends YamlReader[Escalation] with WeakReferenceYamlReader[Escalation] {

  override protected def createReference(implicit source: YamlObject): Escalation = EscalationReference(reference)

  override protected def createDefault(implicit source: YamlObject): Escalation = `type` match {
    case "scale_instances" =>
      ScaleInstancesEscalation(name, <<![Int]("minimum"), <<![Int]("maximum"), <<![Int]("scale_by"))

    case "scale_cpu" =>
      ScaleCpuEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"))

    case "scale_memory" =>
      ScaleMemoryEscalation(name, <<![Double]("minimum"), <<![Double]("maximum"), <<![Double]("scale_by"))

    case generic =>
      GenericEscalation(name, `type`, parameters)
  }
}
