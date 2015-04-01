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

    val environmentVariables = deployment.environmentVariables.filter {
      case (Trait.Name(Some(scope), Some(group), parameterName), value) =>
        deployment.clusters.find(_.name == scope) match {
          case None => false
          case Some(cluster) => cluster.services.map(_.breed).exists { breed =>
            breed.inTraits.exists {
              case t: Port if group == Trait.Name.Group.Ports => t.name.value == parameterName && t.name.scope == None && t.name.group == None
              case EnvironmentVariable(Trait.Name(None, None, traitName), _, _, _) if group == Trait.Name.Group.EnvironmentVariables => traitName == parameterName
              case _ => false
            }
          }
        }
      case _ => false
    }

    DefaultBlueprint(deployment.name, clusters, deployment.endpoints, environmentVariables)
  }
}
