package io.vamp.core.operation.controller

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSystemProvider, ExecutionContextProvider}
import io.vamp.common.http.{InfoMessageBase, InfoRetrieval}
import io.vamp.common.vitals.{JmxVitalsProvider, JvmVitals}
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse.PulseActor
import io.vamp.core.router_driver.RouterDriverActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String, version: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

trait InfoController extends InfoRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider =>

  val infoMessage = ConfigFactory.load().getString("vamp.core.info.message")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.core.info.timeout") seconds)

  def info: Future[InfoMessageBase] = {
    retrieve(PersistenceActor :: RouterDriverActor :: PulseActor :: ContainerDriverActor :: Nil).map { result =>
      InfoMessage(infoMessage,
        getClass.getPackage.getImplementationVersion,
        jvmVitals(),
        result.get(PersistenceActor),
        result.get(RouterDriverActor),
        result.get(PulseActor),
        result.get(ContainerDriverActor)
      )
    }
  }
}
