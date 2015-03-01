package io.magnetic.vamp_core.operation.deployment

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka._
import io.magnetic.vamp_core.model.artifact.{Blueprint, Cluster, DefaultBlueprint}
import io.magnetic.vamp_core.model.deployment.{Deployment, DeploymentCluster}
import io.magnetic.vamp_core.operation.notification.UnsupportedDeploymentRequest
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider

import scala.concurrent.duration._
import scala.language.existentials

object DeploymentActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.update.timeout").seconds)

  def props: Props = Props(new DeploymentActor)

  trait DeploymentMessages

  case class Create(blueprint: DefaultBlueprint) extends DeploymentMessages

  case class Update(name: String, blueprint: DefaultBlueprint) extends DeploymentMessages

  case class Delete(name: String, blueprint: Option[DefaultBlueprint]) extends DeploymentMessages

}

class DeploymentActor extends Actor with ActorLogging with ActorSupport with ReplyActor with InMemoryStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.operation.deployment.DeploymentActor._

  private def uuid = UUID.randomUUID.toString

  lazy implicit val timeout = DeploymentActor.timeout

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = try {
    request match {
      case Create(blueprint) => merge(Deployment(uuid, List(), Map(), Map()), blueprint)
      case Update(name, blueprint) => merge(deploymentFor(name), blueprint)
      case Delete(name, blueprint) => slice(deploymentFor(name), blueprint)
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  private def deploymentFor(name: String): Deployment = {
    implicit val timeout = PersistenceActor.timeout
    actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment]) match {
      case deployment: Deployment => deployment
      case _ => error(ArtifactNotFound(name, classOf[Deployment]))
    }
  }

  private def merge(deployment: Deployment, blueprint: DefaultBlueprint): Deployment = {
    val clusters = mergeClusters(deployment.clusters, blueprint.clusters)
    val endpoints = blueprint.endpoints ++ deployment.endpoints
    val parameters = blueprint.parameters ++ deployment.parameters

    Deployment(deployment.name, clusters, endpoints, parameters)
  }

  private def mergeClusters(deploymentClusters: List[DeploymentCluster], blueprintClusters: List[Cluster]): List[DeploymentCluster] = {

    deploymentClusters
  }

  private def slice(deployment: Deployment, blueprint: Option[Blueprint]): Deployment = {
    deployment
  }
}

