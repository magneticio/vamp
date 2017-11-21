package io.vamp.model.artifact

import io.vamp.common.{ Artifact, Reference, Type, RootAnyMap }

import scala.concurrent.duration.FiniteDuration

object Sla {
  val kind: String = "slas"
}

sealed trait Sla extends Artifact {

  val kind: String = Sla.kind

  def escalations: List[Escalation]
}

case class SlaReference(name: String, escalations: List[Escalation]) extends Reference with Sla

case class GenericSla(name: String, metadata: RootAnyMap, `type`: String, escalations: List[Escalation], parameters: RootAnyMap) extends Sla with Type

case class EscalationOnlySla(name: String, metadata: RootAnyMap, escalations: List[Escalation]) extends Sla with Type {
  def `type` = "escalation_only"
}

sealed trait SlidingWindowSla extends Sla {
  def upper: FiniteDuration

  def lower: FiniteDuration

  def interval: FiniteDuration

  def cooldown: FiniteDuration
}

case class ResponseTimeSlidingWindowSla(name: String, metadata: RootAnyMap, upper: FiniteDuration, lower: FiniteDuration, interval: FiniteDuration, cooldown: FiniteDuration, escalations: List[Escalation]) extends SlidingWindowSla

object Escalation {
  val kind: String = "escalations"
}

sealed trait Escalation extends Artifact {
  val kind: String = Escalation.kind
}

case class EscalationReference(name: String) extends Reference with Escalation

case class GenericEscalation(name: String, metadata: RootAnyMap, `type`: String, parameters: RootAnyMap) extends Escalation with Type

sealed trait GroupEscalation extends Escalation with Type {
  def escalations: List[Escalation]
}

case class ToAllEscalation(name: String, metadata: RootAnyMap, escalations: List[Escalation]) extends GroupEscalation {
  def `type` = "to_all"
}

case class ToOneEscalation(name: String, metadata: RootAnyMap, escalations: List[Escalation]) extends GroupEscalation {
  def `type` = "to_one"
}

sealed trait ScaleEscalation extends Escalation with Type {

  def targetCluster: Option[String]
}

case class ScaleInstancesEscalation(name: String, metadata: RootAnyMap, minimum_Instance: Int, maximum_Instance: Int, scaleBy_Instance: Int, targetCluster: Option[String]) extends ScaleEscalation {
  def `type` = "scale_instances"
}

case class ScaleCpuEscalation(name: String, metadata: RootAnyMap, minimum_Cpu: Double, maximum_Cpu: Double, scaleBy_Cpu: Double, targetCluster: Option[String]) extends ScaleEscalation {
  def `type` = "scale_cpu"
}

case class ScaleMemoryEscalation(name: String, metadata: RootAnyMap, minimum_Memory: Double, maximum_Memory: Double, scaleBy_Memory: Double, targetCluster: Option[String]) extends ScaleEscalation {
  def `type` = "scale_memory"
}

