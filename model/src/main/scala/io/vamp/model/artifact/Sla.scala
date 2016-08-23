package io.vamp.model.artifact

import scala.concurrent.duration.FiniteDuration

trait Sla extends Artifact {

  val kind = "sla"

  def escalations: List[Escalation]
}

case class SlaReference(name: String, escalations: List[Escalation]) extends Reference with Sla

case class GenericSla(name: String, `type`: String, escalations: List[Escalation], parameters: Map[String, Any]) extends Sla with Type

case class EscalationOnlySla(name: String, escalations: List[Escalation]) extends Sla with Type {
  def `type` = "escalation_only"
}

trait SlidingWindowSla[T] extends Sla {
  def upper: T

  def lower: T

  def interval: FiniteDuration

  def cooldown: FiniteDuration
}

case class ResponseTimeSlidingWindowSla(name: String, upper: FiniteDuration, lower: FiniteDuration, interval: FiniteDuration, cooldown: FiniteDuration, escalations: List[Escalation]) extends SlidingWindowSla[FiniteDuration]

trait Escalation extends Artifact {
  val kind = "escalation"
}

case class EscalationReference(name: String) extends Reference with Escalation

case class GenericEscalation(name: String, `type`: String, parameters: Map[String, Any]) extends Escalation with Type

trait GroupEscalation extends Escalation with Type {
  def escalations: List[Escalation]
}

case class ToAllEscalation(name: String, escalations: List[Escalation]) extends GroupEscalation {
  def `type` = "to_all"
}

case class ToOneEscalation(name: String, escalations: List[Escalation]) extends GroupEscalation {
  def `type` = "to_one"
}

trait ScaleEscalation[T] extends Escalation with Type {
  def minimum: T

  def maximum: T

  def scaleBy: T

  def targetCluster: Option[String]
}

case class ScaleInstancesEscalation(name: String, minimum: Int, maximum: Int, scaleBy: Int, targetCluster: Option[String]) extends ScaleEscalation[Int] {
  def `type` = "scale_instances"
}

case class ScaleCpuEscalation(name: String, minimum: Double, maximum: Double, scaleBy: Double, targetCluster: Option[String]) extends ScaleEscalation[Double] {
  def `type` = "scale_cpu"
}

case class ScaleMemoryEscalation(name: String, minimum: Double, maximum: Double, scaleBy: Double, targetCluster: Option[String]) extends ScaleEscalation[Double] {
  def `type` = "scale_memory"
}

