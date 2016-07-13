package io.vamp.common.vitals

object InfoRequest

trait JvmInfoMessage {
  def jvm: Option[JvmVitals]
}