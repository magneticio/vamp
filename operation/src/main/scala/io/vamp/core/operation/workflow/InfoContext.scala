package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorDescription, ActorSupport, FutureSupport}
import io.vamp.common.http.{InfoActor, InfoMessageBase}
import io.vamp.common.vitals.{JmxVitalsProvider, JvmVitals}
import io.vamp.core.container_driver.ContainerDriverActor
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.persistence.PersistenceActor
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.router_driver.RouterDriverActor

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

case class InfoMessage(message: String, jvm: JvmVitals, persistence: Any, router: Any, pulse: Any, containerDriver: Any) extends InfoMessageBase

class InfoContext(actorRefFactory: ActorRefFactory)(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext with JmxVitalsProvider with FutureSupport {

  implicit lazy val timeout: Timeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.info.timeout") seconds)

  val infoMessage = ConfigFactory.load().getString("vamp.core.rest-api.info.message")

  val componentInfoTimeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.info.component-timeout") seconds)

  def info() = serialize {
    val actors = Set(PersistenceActor, RouterDriverActor, PulseDriverActor, ContainerDriverActor).map(ActorSupport.alias)
    offload((actorRefFactory.actorOf(InfoActor.props()) ? InfoActor.GetInfo(actors, componentInfoTimeout)).map {
      case map: Map[_, _] =>
        val result = map.asInstanceOf[Map[ActorDescription, Any]]
        InfoMessage(infoMessage,
          vitals(),
          result.get(ActorSupport.alias(PersistenceActor)),
          result.get(ActorSupport.alias(RouterDriverActor)),
          result.get(ActorSupport.alias(PulseDriverActor)),
          result.get(ActorSupport.alias(ContainerDriverActor))
        )
      case _ => Map()
    })
  }
}
