package io.vamp.core.operation.workflow

import java.time.OffsetDateTime

import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.pulse_driver.model.Aggregator.AggregatorType
import io.vamp.core.pulse_driver.model.{Aggregator, Event, EventQuery, TimeRange}

import scala.concurrent.ExecutionContext

class EventApiContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  private var tags: Set[String] = Set()
  private var _type: Option[String] = None
  private var _value: Option[AnyRef] = None

  private var _lt: Option[String] = None
  private var _lte: Option[String] = None
  private var _gt: Option[String] = None
  private var _gte: Option[String] = None

  def tag(t: String) = tags = tags + t

  def `type`(t: String) = this._type = Some(t)

  def value(v: AnyRef) = this._value = Some(v)

  def publish() = {
    validateTags()

    val event = _type match {
      case None => Event(tags, _value)
      case Some(t) => Event(tags, _value, OffsetDateTime.now(), t)
    }

    logger.info(s"Publishing event: $event")

    reset()
  }

  def lt(time: String) = _lt = {
    if (_lte.isDefined) throw new RuntimeException("Conflict: lte already set.")
    Some(time)
  }

  def lte(time: String) = _lte = {
    if (_lt.isDefined) throw new RuntimeException("Conflict: lt already set.")
    Some(time)
  }

  def gt(time: String) = _gt = {
    if (_gte.isDefined) throw new RuntimeException("Conflict: gte already set.")
    Some(time)
  }

  def gte(time: String) = _gte = {
    if (_gt.isDefined) throw new RuntimeException("Conflict: gt already set.")
    Some(time)
  }

  def query() = eventQuery()

  def count() = eventQuery(Some(Aggregator.count))

  def max() = eventQuery(Some(Aggregator.max))

  def min() = eventQuery(Some(Aggregator.min))

  def average() = eventQuery(Some(Aggregator.average))

  def reset() = {
    tags = Set()
    _type = None
    _value = None
    _lt = None
    _lte = None
    _gt = None
    _gte = None
  }

  private def eventQuery(aggregator: Option[AggregatorType] = None) = {
    validateTags()
    val eventQuery = EventQuery(tags, Some(TimeRange(_lt, _lte, _gt, _gte)), aggregator.flatMap(a => Some(Aggregator(Some(a)))))
    logger.info(s"Event query: $eventQuery")
    reset()
  }

  private def validateTags() = if (tags.isEmpty) throw new RuntimeException("Event tags must be defined.")
}
