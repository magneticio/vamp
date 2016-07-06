package io.vamp.model

import java.time.format.DateTimeFormatter._
import java.time.{ ZoneOffset, ZonedDateTime }
import java.util.UUID

import scala.sys.process._
import scala.util.Try

object Model {

  val uuid = UUID.randomUUID.toString

  val version: String = Option(getClass.getPackage.getImplementationVersion).orElse {
    Try(Option("git describe --tags".!!.stripLineEnd)).getOrElse(None)
  } getOrElse ""

  val runningSince = ZonedDateTime.now(ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME)
}