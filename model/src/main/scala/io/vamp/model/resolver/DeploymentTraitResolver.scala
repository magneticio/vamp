package io.vamp.model.resolver

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.{ HostReference, LocalReference, ValueReference, _ }
import io.vamp.model.notification.UnresolvedDependencyError

trait DeploymentTraitResolver extends TraitResolver {
  this: NotificationProvider ⇒

  def resolveEnvironmentVariables(deployment: Deployment, clusters: List[DeploymentCluster]): List[EnvironmentVariable] = {
    deployment.environmentVariables.map(ev ⇒ TraitReference.referenceFor(ev.name) match {
      case Some(TraitReference(c, g, n)) if g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isEmpty && ev.value.isDefined && clusters.exists(_.name == c) ⇒
        ev.copy(alias = None, interpolated = Some(resolve(ev.value.get, valueFor(deployment, None))))
      case _ ⇒ ev
    })
  }

  def valueFor(deployment: Deployment, service: Option[DeploymentService])(reference: ValueReference): String = (reference match {
    case ref: TraitReference ⇒ deployment.traits.find(_.name == ref.reference).flatMap(_.value)
    case ref: HostReference ⇒ deployment.hosts.find(_.name == ref.asTraitReference).flatMap(_.value)
    case ref: LocalReference if ref.name == s"$marker" ⇒ Some(s"$marker")
    case ref: LocalReference ⇒ service.flatMap(service ⇒ (service.environmentVariables ++ service.breed.constants).find(_.name == ref.name).flatMap(_.value))
    case ref ⇒ None
  }) getOrElse ""

  def valueForWithDependencyReplacement(deployment: Deployment, service: DeploymentService)(reference: ValueReference): String = {
    val aliases = service.breed.dependencies.map {
      case (alias, dependency) ⇒ alias -> (deployment.clusters.find(_.services.exists(_.breed.name == dependency.name)) match {
        case Some(cluster) ⇒ cluster.name
        case None          ⇒ throwException(UnresolvedDependencyError(service.breed, dependency))
      })
    }

    val updated = reference match {
      case ref @ TraitReference(cluster, _, _) ⇒ ref.copy(cluster = aliases.getOrElse(cluster, cluster))
      case HostReference(cluster)              ⇒ HostReference(aliases.getOrElse(cluster, cluster))
      case ref                                 ⇒ ref
    }

    valueFor(deployment, Option(service))(updated)
  }
}
