package io.vamp.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification.{ UnresolvedDependencyInTraitValueError, UnresolvedEnvironmentVariableError, UnresolvedGatewayPortError }
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

  def validateBlueprintTraitValues = validateGatewayPorts andThen validateEnvironmentVariables

  private def validateGatewayPorts: (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint: DefaultBlueprint ⇒

    val ports = blueprint.gateways.flatMap { gateway ⇒
      gateway.routes.map(_.path).map {
        case path if path.path.size == 2 ⇒ gateway.port.copy(name = TraitReference(path.path.head, TraitReference.Ports, path.path.tail.head).reference)
        case path                        ⇒ throwException(UnresolvedGatewayPortError(path.source, gateway.port.value))
      }
    }

    validateVariables(ports, TraitReference.Ports, { port ⇒
      throwException(UnresolvedGatewayPortError(TraitReference.referenceFor(port.name).flatMap(r ⇒ Some(r.referenceWithoutGroup)).getOrElse(port.name), port.value))
    })(blueprint)
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
