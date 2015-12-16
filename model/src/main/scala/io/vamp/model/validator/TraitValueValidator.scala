package io.vamp.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification.{ UnresolvedDependencyInTraitValueError, UnresolvedEnvironmentVariableError }
import io.vamp.model.resolver.TraitResolver

import scala.language.postfixOps

trait BreedTraitValueValidator extends TraitResolver {
  this: NotificationProvider ⇒

  def validateBreedTraitValues(breed: DefaultBreed) = {

    def reportError(ref: ValueReference) =
      throwException(UnresolvedDependencyInTraitValueError(breed, ref.reference))

    def validateCluster(ref: ValueReference) =
      if (!breed.dependencies.keySet.contains(ref.cluster)) reportError(ref)

    def validateDependencyTraitExists(ref: TraitReference) = {
      breed.dependencies.find(_._1 == ref.cluster).map(_._2).foreach {
        case dependency: DefaultBreed ⇒ if (!dependency.traitsFor(ref.group).exists(_.name == ref.name)) reportError(ref)
        case _                        ⇒
      }
    }

    (breed.ports ++ breed.environmentVariables ++ breed.constants).foreach { `trait` ⇒
      `trait`.value.foreach(value ⇒ referencesFor(value).foreach({
        case ref: TraitReference ⇒
          validateCluster(ref)
          validateDependencyTraitExists(ref)

        case ref: HostReference ⇒
          validateCluster(ref)

        case _ ⇒
      }))
    }
  }
}

trait BlueprintTraitValidator extends TraitResolver {
  this: NotificationProvider ⇒

  def validateBlueprintTraitValues = validateGateways andThen validateEnvironmentVariables

  private def validateGateways: (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint: DefaultBlueprint ⇒
    blueprint
  }

  private def validateEnvironmentVariables: (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint: DefaultBlueprint ⇒
    validateVariables(blueprint.environmentVariables, TraitReference.EnvironmentVariables, { ev ⇒
      throwException(UnresolvedEnvironmentVariableError(TraitReference.referenceFor(ev.name).flatMap(r ⇒ Some(r.referenceWithoutGroup)).getOrElse(ev.name), ev.value.getOrElse("")))
    })(blueprint)
  }

  private def validateVariables(variables: List[Trait], group: String, fail: (Trait ⇒ Unit)): (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint: DefaultBlueprint ⇒
    variables.foreach { `trait` ⇒
      TraitReference.referenceFor(`trait`.name) match {
        case Some(TraitReference(cluster, g, name)) if g == group ⇒
          blueprint.clusters.find(_.name == cluster) match {
            case None ⇒ fail(`trait`)
            case Some(c) ⇒
              if (c.services.exists(_.breed match {
                case _: DefaultBreed ⇒ true
                case _               ⇒ false
              }) && !c.services.exists({
                service ⇒
                  service.breed match {
                    case breed: DefaultBreed ⇒ breed.traitsFor(group).exists(_.name.toString == name)
                    case _                   ⇒ false
                  }
              })) fail(`trait`)
          }

        case _ ⇒ fail(`trait`)
      }
    }

    blueprint
  }
}
