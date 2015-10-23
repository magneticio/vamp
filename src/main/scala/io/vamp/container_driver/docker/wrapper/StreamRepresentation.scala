package io.vamp.container_driver.docker.wrapper

import org.json4s._
import org.json4s.native.JsonMethods.parse

object Pull {

  sealed trait Output

  case class Status(message: String) extends Output

  case class ProgressDetail(current: Long, total: Long, start: Long, bar: String)

  /** download progress. details will be None in the case the image has already been downloaded */
  case class Progress(message: String, id: String, details: Option[ProgressDetail]) extends Output

  case class Error(message: String, details: String) extends Output

}

sealed trait StreamRepresentation[T] {
  def map: String ⇒ T
}

object StreamRepresentation {
  implicit val Identity: StreamRepresentation[String] =
    new StreamRepresentation[String] {
      def map = identity(_)
    }

  implicit val Nothing: StreamRepresentation[Unit] =
    new StreamRepresentation[Unit] {
      def map = _ ⇒ ()
    }

  implicit object PullOutputs extends StreamRepresentation[Pull.Output] {
    def map = { str ⇒
      val JObject(obj) = parse(str)

      def progress = (for {
        ("status", JString(message)) ← obj
        ("id", JString(id)) ← obj
        ("progressDetail", JObject(details)) ← obj
      } yield Pull.Progress(message, id, (for {
        ("current", JInt(current)) ← details
        ("total", JInt(total)) ← details
        ("start", JInt(start)) ← details
        ("progress", JString(bar)) ← obj
      } yield Pull.ProgressDetail(
        current.toLong, total.toLong, start.toLong, bar)).headOption)).headOption

      def status = (for {
        ("status", JString(msg)) ← obj
      } yield Pull.Status(msg)).headOption

      def err = (for {
        ("error", JString(msg)) ← obj
        ("errorDetail", JObject(detail)) ← obj
        ("message", JString(details)) ← detail
      } yield Pull.Error(
        msg, details)).headOption

      progress.orElse(status).orElse(err).get
    }
  }

}
