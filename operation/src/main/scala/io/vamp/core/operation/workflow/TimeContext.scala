package io.vamp.core.operation.workflow

import java.time.OffsetDateTime

import io.vamp.common.akka.FutureSupport

class TimeContext() extends FutureSupport {

  def now() = OffsetDateTime.now()

  def epoch() = OffsetDateTime.now().toEpochSecond

  def timestamp() = epoch()
}
