package io.vamp.core.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact.{DefaultBreed, HostReference, TraitReference, ValueReference}
import io.vamp.core.model.notification.UnresolvedDependencyInTraitValueError
import io.vamp.core.model.resolver.TraitValueResolver

import scala.language.postfixOps

trait TraitValueValidator extends TraitValueResolver {
  this: NotificationProvider =>

  def validateBreedTraitValues(breed: DefaultBreed) = {

    def reportError(ref: ValueReference) =
      error(UnresolvedDependencyInTraitValueError(breed, ref.reference))

    def validateCluster(ref: ValueReference) =
      if (!breed.dependencies.keySet.contains(ref.cluster)) reportError(ref)

    def validateDependencyTraitExists(ref: TraitReference) = {
      breed.dependencies.find(_._1 == ref.cluster).map(_._2).foreach {
        case dependency: DefaultBreed => if (!dependency.traitsFor(ref.group).exists(_.name == ref.name)) reportError(ref)
        case _ =>
      }
    }

    (breed.ports ++ breed.environmentVariables ++ breed.constants).foreach { `trait` =>
      resolveReferences(`trait`.value) match {
        case Some(ref: TraitReference) =>
          validateCluster(ref)
          validateDependencyTraitExists(ref)

        case Some(ref: HostReference) =>
          validateCluster(ref)

        case _ =>
      }
    }
  }
}
