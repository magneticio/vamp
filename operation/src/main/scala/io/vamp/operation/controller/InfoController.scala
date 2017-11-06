package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Config, ConfigMagnet, Namespace }
import io.vamp.common.akka.DataRetrieval
import io.vamp.common.akka.IoC._
import io.vamp.common.vitals.{ InfoRequest, JmxVitalsProvider, JvmInfoMessage, JvmVitals }
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.model.Model
import io.vamp.persistence.{ KeyValueStoreActor, PersistenceActor }
import io.vamp.pulse.PulseActor
import io.vamp.workflow_driver.WorkflowDriverActor

import scala.concurrent.Future
import scala.language.postfixOps

trait AbstractInfoMessage extends JvmInfoMessage {
  def message: String

  def version: String

  def uuid: String

  def runningSince: String
}

case class InfoMessage(
  message:         String,
  version:         String,
  uuid:            String,
  runningSince:    String,
  jvm:             Option[JvmVitals],
  persistence:     Option[Any],
  keyValue:        Option[Any],
  pulse:           Option[Any],
  gatewayDriver:   Option[Any],
  containerDriver: Option[Any],
  workflowDriver:  Option[Any]
) extends AbstractInfoMessage

trait InfoController extends AbstractController with DataRetrieval with JmxVitalsProvider {

  val infoMessage: ConfigMagnet[String] = Config.string("vamp.operation.info.message")

  protected val dataRetrievalTimeout: ConfigMagnet[Timeout] = Config.timeout("vamp.operation.info.timeout")

  def infoMessage(on: Set[String])(implicit namespace: Namespace, timeout: Timeout): Future[(AbstractInfoMessage, Boolean)] = {
    retrieve(infoActors(on), actor ⇒ actorFor(actor) ? InfoRequest, dataRetrievalTimeout()) map { result ⇒
      InfoMessage(
        infoMessage(),
        Model.version,
        Model.uuid,
        Model.runningSince,
        if (on.isEmpty || on.contains("jvm")) Option(jvmVitals()) else None,
        result.data.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
        result.data.get(classOf[KeyValueStoreActor].asInstanceOf[Class[Actor]]),
        result.data.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
        result.data.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
        result.data.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]]),
        result.data.get(classOf[WorkflowDriverActor].asInstanceOf[Class[Actor]])
      ) → result.succeeded
    }
  }

  protected def infoActors(on: Set[String]): List[Class[Actor]] = {
    val list = if (on.isEmpty) {
      List(
        classOf[PersistenceActor],
        classOf[KeyValueStoreActor],
        classOf[PulseActor],
        classOf[GatewayDriverActor],
        classOf[ContainerDriverActor],
        classOf[WorkflowDriverActor]
      )
    }
    else on.map(_.toLowerCase).collect {
      case "persistence"      ⇒ classOf[PersistenceActor]
      case "key_value"        ⇒ classOf[KeyValueStoreActor]
      case "pulse"            ⇒ classOf[PulseActor]
      case "gateway_driver"   ⇒ classOf[GatewayDriverActor]
      case "container_driver" ⇒ classOf[ContainerDriverActor]
      case "workflow_driver"  ⇒ classOf[WorkflowDriverActor]
    } toList

    list.map(_.asInstanceOf[Class[Actor]])
  }
}
