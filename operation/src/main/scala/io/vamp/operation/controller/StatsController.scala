package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, DataRetrieval, ExecutionContextProvider }
import io.vamp.common.vitals.{ JmxVitalsProvider, StatsRequest }
import io.vamp.persistence.db.PersistenceActor
import io.vamp.pulse.PulseActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class StatsMessage(persistence: Any, pulse: Any)

trait StatsController extends DataRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  private val dataRetrievalTimeout = Timeout(ConfigFactory.load().getInt("vamp.stats.timeout") seconds)

  def stats: Future[StatsMessage] = {

    val actors = List(classOf[PersistenceActor], classOf[PulseActor]) map {
      _.asInstanceOf[Class[Actor]]
    }

    retrieve(actors, actor ⇒ actorFor(actor) ? StatsRequest, dataRetrievalTimeout) map { result ⇒
      StatsMessage(
        result.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PulseActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
