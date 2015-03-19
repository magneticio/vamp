package io.vamp.common.pulse.api

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/*
  This is Pulse rest api, it is actually an exact copy of whatever we have in pulse project itself - might be a good idea
  to have only one copy of it, so that if the API changes we won't have to change both of them
 */
case class Event(tags: List[String], value: AnyRef, timestamp: OffsetDateTime = OffsetDateTime.now(), `type`: String = "")
case class EventQuery(tags: List[String] = List.empty, time: TimeRange = TimeRange(), aggregator: Option[Aggregator] = Option.empty, `type`: String = "")
case class TimeRange(from: OffsetDateTime = OffsetDateTime.now().minus(100, ChronoUnit.MINUTES), to: OffsetDateTime = OffsetDateTime.now())
case class Aggregator(`type`: String, field: String = "numeric")