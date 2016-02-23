package io.vamp.model

import java.time.format.DateTimeFormatter._
import java.time.{ ZoneOffset, ZonedDateTime }
import java.util.UUID

object Model {

  val uuid = UUID.randomUUID.toString

  val version = Option(getClass.getPackage.getImplementationVersion)

  val runningSince = ZonedDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME)
}