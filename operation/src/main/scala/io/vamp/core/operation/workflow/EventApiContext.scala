package io.vamp.core.operation.workflow

import java.time.OffsetDateTime

import akka.actor.ActorRefFactory
import akka.pattern.ask
import io.vamp.common.akka.{ActorSupport, FutureSupport}
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse.PulseActor.{Publish, Query}
import io.vamp.core.pulse.event.Aggregator.AggregatorType
import io.vamp.core.pulse.event.{Aggregator, Event, EventQuery, TimeRange}
import io.vamp.core.pulse.{EventRequestEnvelope, PulseActor}

import scala.concurrent.ExecutionContext

class EventApiContext(arf: ActorRefFactory)(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext with ActorSupport with FutureSupport {

  implicit lazy val timeout = PersistenceActor.timeout

  private var tags: Set[String] = Set()
  private var _type: Option[String] = None
  private var _value: Option[AnyRef] = None

  private var _lt: Option[String] = None
  private var _lte: Option[String] = None
  private var _gt: Option[String] = None
  private var _gte: Option[String] = None

  private var _page: Int = 1
  private var _perPage: Int = EventRequestEnvelope.max

  def tag(t: String) = {
    tags = tags + t
    this
  }

  def `type`(t: String) = {
    _type = Some(t)
    this
  }

  def value(v: AnyRef) = {
    _value = Some(v)
    this
  }

  def publish() = {
    validateTags()
    val event = _type match {
      case None => Event(tags, _value)
      case Some(t) => Event(tags, _value, OffsetDateTime.now(), t)
    }
    logger.debug(s"Publishing event: $event")
    reset()
    offload(actorFor(PulseActor) ? Publish(event))
  }

  def lt(time: String) = {
    if (_lte.isDefined) throw new RuntimeException("Conflict: lte already set.")
    _lt = Some(time)
    this
  }

  def lte(time: String) = {
    if (_lt.isDefined) throw new RuntimeException("Conflict: lt already set.")
    _lte = Some(time)
    this
  }

  def gt(time: String) = {
    if (_gte.isDefined) throw new RuntimeException("Conflict: gte already set.")
    _gt = Some(time)
    this
  }

  def gte(time: String) = {
    if (_gt.isDefined) throw new RuntimeException("Conflict: gt already set.")
    _gte = Some(time)
    this
  }

  def page(p: Int) = {
    _page = p
    this
  }

  def perPage(pp: Int) = {
    _perPage = pp
    this
  }

  def per_page(pp: Int) = perPage(pp)

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

    _page = 1
    _perPage = EventRequestEnvelope.max

    this
  }

  private def eventQuery(aggregator: Option[AggregatorType] = None) = {
    validateTags()
    val eventQuery = EventQuery(tags, Some(TimeRange(_lt, _lte, _gt, _gte)), aggregator.flatMap(a => Some(Aggregator(Some(a)))))
    logger.info(s"Event query: $eventQuery")
    reset()
    offload(actorFor(PulseActor) ? Query(EventRequestEnvelope(eventQuery, _page, _perPage)))
  }

  private def validateTags() = if (tags.isEmpty) throw new RuntimeException("Event tags must be defined.")

  override implicit def actorRefFactory: ActorRefFactory = arf
}
