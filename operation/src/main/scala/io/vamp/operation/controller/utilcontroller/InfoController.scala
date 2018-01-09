package io.vamp.operation.controller.utilcontroller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.DataRetrieval
import io.vamp.common.akka.IoC._
import io.vamp.common.vitals.{InfoRequest, JmxVitalsProvider, JvmInfoMessage, JvmVitals}
import io.vamp.common.{Config, ConfigMagnet, Namespace}
import io.vamp.container_driver.ContainerDriverActor
import io.vamp.gateway_driver.GatewayDriverActor
import io.vamp.model.Model
import io.vamp.operation.controller.AbstractController
import io.vamp.persistence.KeyValueStoreActor
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.api.PersistenceInfo
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
  persistence:     Option[PersistenceInfo],
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
        message = infoMessage(),
        version = Model.version,
        uuid = Model.uuid,
        runningSince = Model.runningSince,
        jvm = if (on.isEmpty || on.contains("jvm")) Option(jvmVitals()) else None,
        persistence = VampPersistence().info,
        keyValue = result.data.get(classOf[KeyValueStoreActor].asInstanceOf[Class[Actor]]),
        pulse = result.data.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
        gatewayDriver = result.data.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
        containerDriver = result.data.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]]),
        workflowDriver = result.data.get(classOf[WorkflowDriverActor].asInstanceOf[Class[Actor]])
      ) → result.succeeded
    }
  }

  protected def infoActors(on: Set[String]): List[Class[Actor]] = {
    val list = if (on.isEmpty) {
      List(
        classOf[KeyValueStoreActor],
        classOf[PulseActor],
        classOf[GatewayDriverActor],
        classOf[ContainerDriverActor],
        classOf[WorkflowDriverActor]
      )
    }
    else on.map(_.toLowerCase).collect {
      case "key_value"        ⇒ classOf[KeyValueStoreActor]
      case "pulse"            ⇒ classOf[PulseActor]
      case "gateway_driver"   ⇒ classOf[GatewayDriverActor]
      case "container_driver" ⇒ classOf[ContainerDriverActor]
      case "workflow_driver"  ⇒ classOf[WorkflowDriverActor]
    } toList

    list.map(_.asInstanceOf[Class[Actor]])
  }
}
