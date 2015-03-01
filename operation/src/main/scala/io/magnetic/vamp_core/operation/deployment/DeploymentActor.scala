package io.magnetic.vamp_core.operation.deployment

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ReplyActor, RequestError}
import io.magnetic.vamp_core.model.artifact.Blueprint
import io.magnetic.vamp_core.model.deployment.Deployment
import io.magnetic.vamp_core.operation.notification.UnsupportedDeploymentRequest
import io.magnetic.vamp_core.persistence.notification.PersistenceNotificationProvider
import io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider

import scala.concurrent.duration._
import scala.language.existentials

object DeploymentActor extends ActorDescription {

  def props: Props = Props(new DeploymentActor)

  trait DeploymentMessages

  case class Create(blueprint: Blueprint) extends DeploymentMessages

  case class Update(name: String, blueprint: Blueprint) extends DeploymentMessages

  case class Delete(name: String, blueprint: Option[Blueprint]) extends DeploymentMessages

}

class DeploymentActor extends Actor with ActorLogging with ReplyActor with InMemoryStoreProvider with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import io.magnetic.vamp_core.operation.deployment.DeploymentActor._

  private def uuid = UUID.randomUUID.toString

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.update.timeout").seconds)

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = request match {
    case Create(blueprint) => merge(Deployment(uuid, List(), Map(), Map()), blueprint)
    case Update(name, blueprint) => merge(Deployment(uuid, List(), Map(), Map()), blueprint)
    case Delete(name, blueprint) => slice(Deployment(uuid, List(), Map(), Map()), blueprint)
    case _ => exception(errorRequest(request))
  }

  private def merge(deployment: Deployment, blueprint: Blueprint): Deployment = {
    deployment
  }

  private def slice(deployment: Deployment, blueprint: Option[Blueprint]): Deployment = {
    deployment
  }
}

