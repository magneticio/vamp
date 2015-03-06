package io.magnetic.vamp_core.container_driver

import io.magnetic.vamp_core.model.artifact.{DefaultBreed, DefaultScale, Deployment, DeploymentServer}

import scala.concurrent.Future


case class ContainerService(deploymentName: String, breedName: String, servers: List[DeploymentServer])

trait ContainerDriver {

  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, breed: DefaultBreed, scale: DefaultScale): Unit

  def undeploy(deployment: Deployment, breed: DefaultBreed): Unit
}
