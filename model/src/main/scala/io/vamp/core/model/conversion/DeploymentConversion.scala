package io.vamp.core.model.conversion

import io.vamp.core.model.artifact._

import scala.language.implicitConversions

object DeploymentConversion {
  implicit def deploymentConversion(deployment: Deployment): DeploymentConversion = new DeploymentConversion(deployment)
}

class DeploymentConversion(val deployment: Deployment) {

  def asBlueprint: DefaultBlueprint = {
    def purgeEnvironmentVariables(cluster: DeploymentCluster, service: DeploymentService) = service.environmentVariables.filter { ev =>
      val dev = deployment.environmentVariables.find(v => v.name == TraitReference(cluster.name, TraitReference.EnvironmentVariables, ev.name).reference).getOrElse(ev)
      val bev = service.breed.environmentVariables.find(v => v.name == ev.name).getOrElse(ev)
      dev.value != ev.value && bev.value != ev.value
    }

    val clusters = deployment.clusters.map(cluster => {
      Cluster(cluster.name, cluster.services.map(service => Service(service.breed, purgeEnvironmentVariables(cluster, service), service.scale, service.routing, service.dialects)), cluster.sla, cluster.dialects)
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
    } map (_.copy(interpolated = None))

    DefaultBlueprint(deployment.name, clusters, deployment.endpoints, environmentVariables)
  }
}
