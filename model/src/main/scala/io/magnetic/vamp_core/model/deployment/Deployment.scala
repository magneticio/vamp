package io.magnetic.vamp_core.model.deployment

import io.magnetic.vamp_core.model.artifact._


case class Deployment(name: String, clusters: List[DeploymentCluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Blueprint

case class DeploymentCluster(name: String, services: List[DeploymentService], sla: Option[Sla]) extends AbstractCluster

case class DeploymentService(breed: DefaultBreed, scale: Option[Scale], routing: Option[Routing]) extends AbstractService
