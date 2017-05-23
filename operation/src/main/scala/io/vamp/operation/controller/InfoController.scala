package io.vamp.operation.controller

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Config, Namespace, NamespaceInfo }
import io.vamp.common.akka.{ DataRetrieval, ReplyCheck }
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
  namespace:       Option[NamespaceInfo],
  persistence:     Option[Any],
  keyValue:        Option[Any],
  pulse:           Option[Any],
  gatewayDriver:   Option[Any],
  containerDriver: Option[Any],
  workflowDriver:  Option[Any]
) extends AbstractInfoMessage

trait InfoController extends AbstractController with DataRetrieval with JmxVitalsProvider with ArtifactApiController with ReplyCheck {

  val infoMessage = Config.string("vamp.operation.info.message")

  protected val dataRetrievalTimeout = Config.timeout("vamp.operation.info.timeout")

  def infoMessage(on: Set[String])(implicit namespace: Namespace, timeout: Timeout): Future[(AbstractInfoMessage, Boolean)] = {
    retrieve(infoActors(on), actor ⇒ actorFor(actor) ? InfoRequest, dataRetrievalTimeout()) flatMap { result ⇒
      fullNamespaceInfoOpt(on).map { namespaceInfoOpt ⇒
        InfoMessage(
          infoMessage(),
          Model.version,
          Model.uuid,
          Model.runningSince,
          if (on.isEmpty || on.contains("jvm")) Some(jvmVitals()) else None,
          namespaceInfoOpt,
          result.data.get(classOf[PersistenceActor].asInstanceOf[Class[Actor]]),
          result.data.get(classOf[KeyValueStoreActor].asInstanceOf[Class[Actor]]),
          result.data.get(classOf[PulseActor].asInstanceOf[Class[Actor]]),
          result.data.get(classOf[GatewayDriverActor].asInstanceOf[Class[Actor]]),
          result.data.get(classOf[ContainerDriverActor].asInstanceOf[Class[Actor]]),
          result.data.get(classOf[WorkflowDriverActor].asInstanceOf[Class[Actor]])
        ) → result.succeeded
      }
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

  def fullNamespaceInfoOpt(on: Set[String])(implicit namespace: Namespace): Future[Option[NamespaceInfo]] = {
    implicit val timeout = PersistenceActor.timeout()
    if (on.contains("namespace")) checked[Option[Namespace]](
      readArtifact(
        namespace.kind,
        namespace.name,
        expandReferences = true,
        onlyReferences = true)(
        namespace = Namespace(namespace.parent),
        timeout)
    ).map(_.map(NamespaceInfo(_)))
    else
      Future.successful(None)
  }

}
