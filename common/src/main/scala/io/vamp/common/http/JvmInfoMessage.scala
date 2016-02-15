package io.vamp.common.http

import io.vamp.common.vitals.JvmVitals

trait JvmInfoMessage {
  def jvm: JvmVitals
}
