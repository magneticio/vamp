package io.vamp.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification.{ MissingEnvironmentVariableError, UnresolvedDependencyInTraitValueError, UnresolvedEnvironmentVariableError, UnresolvedGatewayPortError }
import io.vamp.model.resolver.{ ValueResolver, VariableNode }

trait BreedTraitValueValidator extends ValueResolver {
  this: NotificationProvider ⇒

  def validateBreedTraitValues(breed: DefaultBreed) = {

    def reportError(ref: ClusterReference) =
      throwException(UnresolvedDependencyInTraitValueError(breed, ref.reference))

    def validateCluster(ref: ClusterReference) =
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

  def validateEnvironmentVariablesAgainstBreed(environmentVariables: List[EnvironmentVariable], breed: Breed) = breed match {
    case breed: DefaultBreed ⇒ environmentVariables.foreach { environmentVariable ⇒
      if (environmentVariable.value.isEmpty) throwException(MissingEnvironmentVariableError(breed, environmentVariable.name))
      if (!breed.environmentVariables.exists(_.name == environmentVariable.name)) throwException(UnresolvedDependencyInTraitValueError(breed, environmentVariable.name))
    }
    case _ ⇒
  }
}

trait BlueprintTraitValidator extends ValueResolver {
  this: NotificationProvider ⇒

  def validateBlueprintTraitValues = validateGatewayPorts andThen validateEnvironmentVariables andThen validateInterpolatedValues

  private def validateGatewayPorts: (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint: DefaultBlueprint ⇒

    val ports = blueprint.gateways.flatMap { gateway ⇒
      gateway.routes.map {
        case route if route.length == 2 ⇒ gateway.port.copy(name = TraitReference(route.path.segments.head, TraitReference.Ports, route.path.segments.tail.head).reference)
        case route                      ⇒ throwException(UnresolvedGatewayPortError(route.path.source, gateway.port.value))
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
              if (c.services.forall(_.breed.isInstanceOf[DefaultBreed]) && !c.services.exists({
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

  private def validateInterpolatedValues: (DefaultBlueprint ⇒ DefaultBlueprint) = { blueprint ⇒

    def fail(ev: EnvironmentVariable) = {
      throwException(UnresolvedEnvironmentVariableError(TraitReference.referenceFor(ev.name).flatMap(r ⇒ Some(r.referenceWithoutGroup)).getOrElse(ev.name), ev.value.getOrElse("")))
    }

    val environmentVariables: List[EnvironmentVariable] = blueprint.clusters.flatMap(_.services).collect {
      case service if service.breed.isInstanceOf[DefaultBreed] ⇒ service.breed.asInstanceOf[DefaultBreed].environmentVariables
    }.flatten ++ blueprint.environmentVariables

    environmentVariables.filter(_.value.isDefined).foreach { ev ⇒
      nodes(ev.value.get).foreach {
        case VariableNode(TraitReference(cluster, group, name)) ⇒
          blueprint.clusters.find(_.name == cluster) match {
            case None ⇒
            case Some(c) ⇒
              if (c.services.forall(_.breed.isInstanceOf[DefaultBreed]) && !c.services.exists { service ⇒
                service.breed match {
                  case breed: DefaultBreed ⇒ breed.traitsFor(group).exists(_.name.toString == name)
                  case _                   ⇒ false
                }
              }) fail(ev)
          }
        case _ ⇒
      }
    }

    blueprint
  }
}
