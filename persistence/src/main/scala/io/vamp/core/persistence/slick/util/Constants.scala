package io.vamp.core.persistence.slick.util

object Constants {

  val Escalation_To_One: String = "to_one"
  val Escalation_To_all: String = "to_all"
  val Escalation_Scale_Instances: String = "scale_instances"
  val Escalation_Scale_Cpu: String = "scale_cpu"
  val Escalation_Scale_Memory: String = "scale_memory"

  val Sla_Escalation_Only: String = "escalation_only"
  val Sla_Response_Time_Sliding_Window = "response_time_sliding_window"

}
