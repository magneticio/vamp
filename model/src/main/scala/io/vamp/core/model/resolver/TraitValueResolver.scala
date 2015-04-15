package io.vamp.core.model.resolver

import io.vamp.core.model.artifact._

trait TraitValueResolver {

  def resolveEnvironmentVariables(deployment: Deployment, clusters: List[DeploymentCluster], environmentVariables: List[EnvironmentVariable]): List[EnvironmentVariable] = {
    environmentVariables.map(ev => ev.value match {
      case Some(value) =>
        if (value.startsWith("$") && !value.startsWith("$$")) {
          val reference = value.substring(1)

          (TraitReference.referenceFor(reference) match {
            case Some(TraitReference(cluster, _, _)) if clusters.exists(_.name == cluster) => deployment.traits.find(_.name == reference).flatMap(_.value)
            case None => HostReference.referenceFor(reference) match {
              case Some(hostReference) if clusters.exists(_.name == hostReference.cluster) => deployment.hosts.find(_.name == hostReference.asTraitReference).flatMap(_.value)
              case _ => None
            }
            case _ => None
          }) match {
            case Some(v) => ev.copy(value = Some(v))
            case _ => ev
          }
        } else ev
      case None => ev
    })
  }
}
