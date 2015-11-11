package io.vamp.gateway_driver.kibana

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.model.artifact.{ Deployment, DeploymentCluster }
import io.vamp.model.event.Event
import io.vamp.pulse.Percolator.{ RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.{ PulseActor, PulseEventTags }

import scala.concurrent.Future
import scala.language.postfixOps

object KibanaDashboardActor {

  object KibanaDashboardUpdate

  object KibanaDashboardDelete

  val configuration = ConfigFactory.load().getConfig("vamp.gateway-driver.kibana")

  val enabled = configuration.getBoolean("enabled")

  val logstashIndex = configuration.getString("logstash-index")

  val elasticsearchUrl = configuration.getString("elasticsearch.url")
}

class KibanaDashboardActor extends CommonSupportForActors with GatewayDriverNotificationProvider {

  import KibanaDashboardActor._
  import PulseEventTags.DeploymentSynchronization._

  private val percolator = "kibana://"

  def receive: Receive = {
    case Start ⇒ start()
    case Shutdown ⇒ shutdown()
    case InfoRequest ⇒ reply(info)
    case (KibanaDashboardUpdate, Event(_, (deployment: Deployment, cluster: DeploymentCluster), _, _)) ⇒ update(deployment, cluster)
    case (KibanaDashboardDelete, Event(_, (deployment: Deployment, cluster: DeploymentCluster), _, _)) ⇒ delete(deployment, cluster)
    case _ ⇒
  }

  private def info = Future.successful("enabled" -> enabled)

  private def start() = if (enabled) {
    IoC.actorFor[PulseActor] ! RegisterPercolator(s"${percolator}update", Set(updateTag), KibanaDashboardUpdate)
    IoC.actorFor[PulseActor] ! RegisterPercolator(s"${percolator}delete", Set(deleteTag), KibanaDashboardDelete)
  }

  private def shutdown() = if (enabled) {
    IoC.actorFor[PulseActor] ! UnregisterPercolator(s"${percolator}update")
    IoC.actorFor[PulseActor] ! UnregisterPercolator(s"${percolator}delete")
  }

  private def update(deployment: Deployment, cluster: DeploymentCluster) = {
    //println(s"update: ${deployment.name} -> ${cluster.name}")
  }

  private def delete(deployment: Deployment, cluster: DeploymentCluster) = {
    //println(s"delete: ${deployment.name} -> ${cluster.name}")
  }
}
