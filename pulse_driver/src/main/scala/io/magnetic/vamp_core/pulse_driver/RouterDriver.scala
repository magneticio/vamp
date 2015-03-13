package io.magnetic.vamp_core.pulse_driver

import io.magnetic.vamp_core.model.artifact.{Deployment, DeploymentCluster}

import scala.concurrent.{ExecutionContext, Future}

trait PulseDriver {

  def lastSlaEvent(deployment: Deployment, cluster: DeploymentCluster): Future[Any]

}

class DefaultPulseDriver(ec: ExecutionContext, url: String) extends PulseDriver {
  protected implicit val executionContext = ec

  def lastSlaEvent(deployment: Deployment, cluster: DeploymentCluster) = Future {}
}
