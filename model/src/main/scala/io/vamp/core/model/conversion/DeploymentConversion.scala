package io.vamp.core.model.conversion

import io.vamp.core.model.artifact._

import scala.language.implicitConversions

object DeploymentConversion {
  implicit def deploymentConversion(deployment: Deployment): DeploymentConversion = new DeploymentConversion(deployment)
}

class DeploymentConversion(val deployment: Deployment) {

  def asBlueprint: DefaultBlueprint = {
    val clusters = deployment.clusters.map(cluster => {
      Cluster(cluster.name, cluster.services.map(service => Service(service.breed, service.scale, service.routing)), cluster.sla)
    })

    val environmentVariables = deployment.environmentVariables.filter { ev =>
      TraitReference.referenceFor(ev.name) match {
        case Some(TraitReference(cluster, group, name)) =>
          deployment.clusters.find(_.name == cluster) match {
            case None => false
            case Some(c) => c.services.map(_.breed).exists { breed =>
              breed.traitsFor(group).exists(_.name == name)
            }
          }
        case _ => false
      }
    }

    DefaultBlueprint(deployment.name, clusters, deployment.endpoints, environmentVariables)
  }
}
