package io.magnetic.vamp_core.container_driver

import io.magnetic.vamp_core.model.artifact._

import scala.concurrent.Future


case class ContainerService(matching: (Deployment, Breed) => Boolean, scale: DefaultScale, servers: List[DeploymentServer])

trait ContainerDriver {

  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, breed: DefaultBreed, scale: DefaultScale): Future[Any]

  def undeploy(deployment: Deployment, breed: DefaultBreed): Future[Any]
}
