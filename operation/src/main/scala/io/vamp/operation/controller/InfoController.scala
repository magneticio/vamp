package io.vamp.operation.controller

import akka.actor.Actor
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.http.{ InfoMessageBase, InfoRetrieval }
import io.vamp.common.vitals.{ JmxVitalsProvider, JvmVitals }
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.model.Model
import io.vamp.persistence.db.PersistenceActor
import io.vamp.pulse.PulseActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String,
                       version: String,
                       uuid: String,
                       runningSince: String,
                       jvm: JvmVitals,
                       persistence: Any,
                       gateway: Any,
                       pulse: Any,
                       containerDriver: Any) extends InfoMessageBase

trait InfoController extends InfoRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  val infoMessage = ConfigFactory.load().getString("vamp.info.message")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.info.timeout") seconds)

  def info: Future[InfoMessageBase] = {
    val actors = List(classOf[PersistenceActor], classOf[GatewayDriverActor], classOf[PulseActor], classOf[ContainerDriverActor])

    retrieve(actors.map(_.asInstanceOf[Class[Actor]])).map { result ⇒
      InfoMessage(infoMessage,
        Model.version.orNull,
        Model.uuid,
        Model.runningSince,
        jvmVitals(),
        result.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
