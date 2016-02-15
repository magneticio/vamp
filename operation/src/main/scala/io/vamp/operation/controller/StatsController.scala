package io.vamp.operation.controller

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ ActorSystemProvider, DataRetrieval, ExecutionContextProvider }
import io.vamp.common.vitals.JmxVitalsProvider

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class StatsMessage(message: String)

trait StatsController extends DataRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  private val dataRetrievalTimeout = Timeout(ConfigFactory.load().getInt("vamp.stats.timeout") seconds)

  def stats: Future[StatsMessage] = {
    retrieve(Nil, actor ⇒ Future.successful(actor), dataRetrievalTimeout).map {
      result ⇒ StatsMessage("stats")
    }
  }
}
