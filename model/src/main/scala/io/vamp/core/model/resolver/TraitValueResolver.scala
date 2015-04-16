package io.vamp.core.model.resolver

import io.vamp.core.model.artifact._

trait TraitValueResolver {

  val marker = "$"

  def resolveReferences(value: Option[String]): Option[ValueReference] = value.flatMap(resolveReferences)

  def resolveReferences(value: String): Option[ValueReference] = {
    if (value.startsWith(marker) && !value.startsWith(s"$marker$marker")) {
      val reference = value.substring(1)
      TraitReference.referenceFor(reference).orElse(HostReference.referenceFor(reference))
    } else None
  }
}

trait DeploymentTraitValueResolver extends TraitValueResolver {

  def resolveEnvironmentVariables(deployment: Deployment, clusters: List[DeploymentCluster], environmentVariables: List[EnvironmentVariable]): List[EnvironmentVariable] = {
    environmentVariables.map(ev => ev.value match {
      case Some(value) => (resolveReferences(value) match {
        case Some(ref: TraitReference) if clusters.exists(_.name == ref.cluster) => deployment.traits.find(_.name == ref.reference).flatMap(_.value)
        case Some(ref: HostReference) if clusters.exists(_.name == ref.cluster) => deployment.hosts.find(_.name == ref.asTraitReference).flatMap(_.value)
        case _ => None
      }) match {
        case Some(v) => ev.copy(value = Some(v))
        case _ => ev
      }
      case None => ev
    })
  }
}
