package io.vamp.operation.controller.utilcontroller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.DataRetrieval
import io.vamp.common.akka.IoC._
import io.vamp.common.vitals.{ JmxVitalsProvider, JvmVitals, StatsRequest }
import io.vamp.common.{ Config, Namespace }
import io.vamp.operation.controller.AbstractController
import io.vamp.operation.metrics.KamonMetricsActor
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.pulse.PulseActor

import scala.concurrent.Future

case class StatsMessage(jvm: JvmVitals, system: Any, persistence: String, pulse: Any)

trait StatsController extends AbstractController with DataRetrieval with JmxVitalsProvider {

  private val dataRetrievalTimeout = Config.timeout("vamp.operation.stats.timeout")

  def stats()(implicit namespace: Namespace, timeout: Timeout): Future[StatsMessage] = {

    val actors = List(classOf[KamonMetricsActor], classOf[PulseActor]) map {
      _.asInstanceOf[Class[Actor]]
    }

    retrieve(actors, actor ⇒ actorFor(actor) ? StatsRequest, dataRetrievalTimeout()) map { result ⇒
      StatsMessage(
        jvmVitals(),
        result.data.get(classOf[KamonMetricsActor].asInstanceOf[Class[Actor]]),
        VampPersistence().info,
        result.data.get(classOf[PulseActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
