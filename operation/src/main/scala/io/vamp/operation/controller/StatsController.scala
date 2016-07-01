package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, DataRetrieval, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.vitals.{ JmxVitalsProvider, JvmVitals, StatsRequest }
import io.vamp.operation.metrics.KamonMetricsActor
import io.vamp.persistence.db.PersistenceActor
import io.vamp.pulse.PulseActor

import scala.concurrent.Future

case class StatsMessage(jvm: JvmVitals, system: Any, persistence: Any, pulse: Any)

trait StatsController extends DataRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  private val dataRetrievalTimeout = Config.timeout("vamp.stats.timeout")

  def stats: Future[StatsMessage] = {

    val actors = List(classOf[KamonMetricsActor], classOf[PersistenceActor], classOf[PulseActor]) map {
      _.asInstanceOf[Class[Actor]]
    }

    retrieve(actors, actor ⇒ actorFor(actor) ? StatsRequest, dataRetrievalTimeout) map { result ⇒
      StatsMessage(
        jvmVitals(),
        result.get(classOf[KamonMetricsActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PulseActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
