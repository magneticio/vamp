package io.vamp.operation.controller.utilcontroller

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC.actorFor
import io.vamp.model.event.{Event, EventQuery, TimeRange}
import io.vamp.operation.controller.AbstractController
import io.vamp.pulse.{EventRequestEnvelope, EventResponseEnvelope, PulseActor}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait EventPeekController extends AbstractController {

  def peekLastDoubleValue(tags: List[String], window: FiniteDuration)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] =
    for {
      last <- lastDoubleValue(tags.toSet, window)
    } yield {
      last.flatMap{ value =>
        Try(Some(value.toString.asInstanceOf[Double])).getOrElse(None)
      }
    }

  private def lastDoubleValue(tags: Set[String], window: FiniteDuration, `type`: Option[String] = None)(implicit namespace: Namespace, timeout: Timeout): Future[Option[AnyRef]] = {
    val eventQuery = EventQuery(tags, `type`, Option(timeRange(window)), None)
    actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
      case EventResponseEnvelope(Event(_, _, value, _, _) :: _, _, _, _) ⇒ Option(value)
      case _ ⇒ None
    }
  }

  private def timeRange(window: FiniteDuration) = {
    val now = OffsetDateTime.now()
    val from = now.minus(window.toSeconds, ChronoUnit.SECONDS)
    TimeRange(Some(from), Some(now), includeLower = true, includeUpper = true)
  }
}
