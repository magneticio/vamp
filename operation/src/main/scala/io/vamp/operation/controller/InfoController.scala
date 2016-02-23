package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, DataRetrieval, ExecutionContextProvider }
import io.vamp.common.vitals.{ InfoRequest, JmxVitalsProvider, JvmInfoMessage, JvmVitals }
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.model.Model
import io.vamp.persistence.db.PersistenceActor
import io.vamp.pulse.PulseActor
import io.vamp.workflow_driver.WorkflowDriverActor

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String,
                       version: String,
                       uuid: String,
                       runningSince: String,
                       jvm: JvmVitals,
                       persistence: Any,
                       pulse: Any,
                       gatewayDriver: Any,
                       containerDriver: Any,
                       workflowDriver: Any) extends JvmInfoMessage

trait InfoController extends DataRetrieval with JmxVitalsProvider {
  this: ExecutionContextProvider with ActorSystemProvider ⇒

  implicit def timeout: Timeout

  val infoMessage = ConfigFactory.load().getString("vamp.info.message")

  private val dataRetrievalTimeout = Timeout(ConfigFactory.load().getInt("vamp.info.timeout") seconds)

  def info: Future[JvmInfoMessage] = {

    val actors = List(classOf[PersistenceActor], classOf[PulseActor], classOf[GatewayDriverActor], classOf[ContainerDriverActor], classOf[WorkflowDriverActor]) map {
      _.asInstanceOf[Class[Actor]]
    }

    retrieve(actors, actor ⇒ actorFor(actor) ? InfoRequest, dataRetrievalTimeout) map { result ⇒
      InfoMessage(infoMessage,
        Model.version.orNull,
        Model.uuid,
        Model.runningSince,
        jvmVitals(),
        result.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]]),
        result.get(classOf[WorkflowDriverActor].asInstanceOf[Class[Actor]])
      )
    }
  }
}
