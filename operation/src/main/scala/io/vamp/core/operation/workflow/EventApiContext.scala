package io.vamp.core.operation.workflow

import java.time.OffsetDateTime

import akka.actor.ActorRefFactory
import akka.pattern.ask
import io.vamp.common.akka.ActorSupport
import io.vamp.core.model.event.Aggregator.AggregatorType
import io.vamp.core.model.event._
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse.PulseActor.{Publish, Query}
import io.vamp.core.pulse.{EventRequestEnvelope, EventResponseEnvelope, PulseActor}

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

class EventApiContext(arf: ActorRefFactory)(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext with ActorSupport {

  implicit lazy val timeout = PersistenceActor.timeout

  private var tags: Set[String] = Set()
  private var _type: Option[String] = None
  private var _value: Option[AnyRef] = None

  private var _lt: Option[String] = None
  private var _lte: Option[String] = None
  private var _gt: Option[String] = None
  private var _gte: Option[String] = None

  private var _field: Option[String] = None

  private var _page: Int = 1
  private var _perPage: Int = EventRequestEnvelope.maxPerPage

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

  def publish() = serialize {
    validateTags()
    val event = _type match {
      case None => Event(tags, _value)
      case Some(t) => Event(tags, _value, OffsetDateTime.now(), t)
    }
    logger.debug(s"Publishing event: $event")
    reset()
    async(await(actorFor(PulseActor) ? Publish(event)))
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

  def field(f: String) = {
    _field = Some(f)
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

  def sum() = eventQuery(Some(Aggregator.sum))

  def avg() = average()

  def average() = eventQuery(Some(Aggregator.average))

  def reset() = {
    tags = Set()

    _type = None
    _value = None

    _lt = None
    _lte = None
    _gt = None
    _gte = None

    _field = None

    _page = 1
    _perPage = EventRequestEnvelope.maxPerPage

    this
  }

  private def eventQuery(aggregator: Option[AggregatorType] = None) = serialize {
    validateTags()
    val eventQuery = EventQuery(tags, Some(TimeRange(_lt, _lte, _gt, _gte)), aggregator.flatMap(agg => Some(Aggregator(agg, _field))))
    logger.info(s"Event query: $eventQuery")
    reset()
    async {
      await {
        actorFor(PulseActor) ? Query(EventRequestEnvelope(eventQuery, _page, _perPage)) map {
          case EventResponseEnvelope(list, _, _, _) => list
          case result: SingleValueAggregationResult[_] => result.value
          case other => other
        }
      }
    }
  }

  private def validateTags() = if (tags.isEmpty) throw new RuntimeException("Event tags must be defined.")

  override implicit def actorRefFactory: ActorRefFactory = arf
}
