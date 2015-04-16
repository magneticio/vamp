package io.vamp.core.model.validator

import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.{UnresolvedEnvironmentVariableError, UnresolvedDependencyInTraitValueError, UnresolvedEndpointPortError}
import io.vamp.core.model.resolver.TraitValueResolver

import scala.language.postfixOps

trait BreedTraitValueValidator extends TraitValueResolver {
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

trait BlueprintTraitValueValidator extends TraitValueResolver {
  this: NotificationProvider =>

  def validateBlueprintTraitValues = validateEndpoints andThen validateEnvironmentVariables

  private def validateEndpoints: (DefaultBlueprint => DefaultBlueprint) = { blueprint: DefaultBlueprint =>
    def reportError(endpoint: Port) =
      error(UnresolvedEndpointPortError(endpoint.name, endpoint.value))

    blueprint.endpoints.foreach { endpoint =>
      resolveReferences(s"$marker${endpoint.name}") match {
        case Some(ref: TraitReference) if ref.group == TraitReference.groupFor(TraitReference.Ports) =>
          blueprint.clusters.find(_.name == ref.cluster) match {
            case None => reportError(endpoint)
            case Some(cluster) =>
              if (cluster.services.exists(_.breed match {
                case _: DefaultBreed => true
                case _ => false
              }) && cluster.services.find({
                service => service.breed match {
                  case breed: DefaultBreed => breed.ports.exists(_.name.toString == ref.name)
                  case _ => false
                }
              }).isEmpty) reportError(endpoint)
          }

        case _ => reportError(endpoint)
      }
    }

    blueprint
  }

  private def validateEnvironmentVariables: (DefaultBlueprint => DefaultBlueprint) = { blueprint: DefaultBlueprint =>
    def reportError(ev: EnvironmentVariable) =
      error(UnresolvedEnvironmentVariableError(ev.name, ev.value))

    blueprint.environmentVariables.foreach { ev =>
      resolveReferences(s"$marker${ev.name}") match {
        case Some(ref: TraitReference) if ref.group == TraitReference.groupFor(TraitReference.EnvironmentVariables) =>
          blueprint.clusters.find(_.name == ref.cluster) match {
            case None => reportError(ev)
            case Some(cluster) =>
              if (cluster.services.exists(_.breed match {
                case _: DefaultBreed => true
                case _ => false
              }) && cluster.services.find({
                service => service.breed match {
                  case breed: DefaultBreed => breed.environmentVariables.exists(_.name.toString == ref.name)
                  case _ => false
                }
              }).isEmpty) reportError(ev)
          }

        case _ => reportError(ev)
      }
    }

    blueprint
  }

  //    blueprint.environmentVariables.find({
  //      case (Trait.Name(Some(scope), Some(group), name), _) =>
  //        blueprint.clusters.find(_.name == scope) match {
  //          case None => true
  //          case Some(cluster) => cluster.services.exists(_.breed match {
  //            case _: DefaultBreed => true
  //            case _ => false
  //          }) && cluster.services.find({
  //            service => service.breed match {
  //              case breed: DefaultBreed => breed.inTraits.exists(_.name.toString == name)
  //              case _ => false
  //            }
  //          }).isEmpty
  //        }
  //      case _ => true
  //    }).flatMap {
  //      case (name, value) => error(UnresolvedParameterError(name, value))
  //    }

}
