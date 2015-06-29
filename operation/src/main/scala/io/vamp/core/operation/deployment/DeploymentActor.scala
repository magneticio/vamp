package io.vamp.core.operation.deployment

import java.util.UUID

import _root_.io.vamp.common.akka._
import _root_.io.vamp.core.operation.deployment.DeploymentSynchronizationActor.Synchronize
import akka.actor.Props
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.dictionary.DictionaryActor
import io.vamp.core.model.artifact.DeploymentService.{ReadyForDeployment, ReadyForUndeployment}
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification._
import io.vamp.core.model.reader.{BlueprintReader, BreedReader}
import io.vamp.core.model.resolver.DeploymentTraitResolver
import io.vamp.core.operation.notification._
import io.vamp.core.persistence.{ArtifactSupport, PersistenceActor}
import io.vamp.core.persistence.notification.PersistenceOperationFailure

import scala.language.{existentials, postfixOps}

object DeploymentActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentActor]

  trait DeploymentMessages

  case class Create(blueprint: Blueprint, source: String) extends DeploymentMessages

  case class Merge(name: String, blueprint: Blueprint, source: String) extends DeploymentMessages

  case class Slice(name: String, blueprint: Blueprint, source: String) extends DeploymentMessages

  case class UpdateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) extends DeploymentMessages

  case class UpdateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) extends DeploymentMessages

  case class UpdateRouting(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, routing: DefaultRouting, source: String) extends DeploymentMessages

}

class DeploymentActor extends CommonReplyActor with BlueprintSupport with DeploymentValidator with DeploymentMerger with DeploymentSlicer with DeploymentUpdate with ArtifactSupport with OperationNotificationProvider {

  import DeploymentActor._

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = try {
    request match {
      case Create(blueprint, source) => (merge(deploymentFor(blueprint)) andThen commit(create = true, source))(deploymentFor())

      case Merge(name, blueprint, source) => (merge(deploymentFor(blueprint)) andThen commit(create = true, source))(deploymentFor(name))

      case Slice(name, blueprint, source) => (slice(deploymentFor(blueprint)) andThen commit(create = false, source))(deploymentFor(name))

      case UpdateSla(deployment, cluster, sla, source) => updateSla(deployment, cluster, sla, source)

      case UpdateScale(deployment, cluster, service, scale, source) => updateScale(deployment, cluster, service, scale, source)

      case UpdateRouting(deployment, cluster, service, routing, source) => updateRouting(deployment, cluster, service, routing, source)

      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  def commit(create: Boolean, source: String): (Deployment => Deployment) = { (deployment: Deployment) =>
    implicit val timeout: Timeout = PersistenceActor.timeout
    offload(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment, Some(source), create = create)) match {
      case persisted: Deployment =>
        actorFor(DeploymentSynchronizationActor) ! Synchronize(persisted)
        persisted
      case any => error(errorRequest(PersistenceOperationFailure(any)))
    }
  }
}

trait BlueprintSupport {
  this: ArtifactSupport =>

  private def uuid = UUID.randomUUID.toString

  def deploymentFor(): Deployment = Deployment(uuid, Nil, Nil, Nil, Nil, Nil, Nil)

  def deploymentFor(name: String): Deployment = artifactFor[Deployment](name)

  def deploymentFor(blueprint: Blueprint): Deployment = {
    val bp = artifactFor[DefaultBlueprint](blueprint)

    val clusters = bp.clusters.map { cluster =>
      DeploymentCluster(cluster.name, cluster.services.map { service =>
        DeploymentService(ReadyForDeployment(), artifactFor[DefaultBreed](service.breed), artifactFor[DefaultScale](service.scale), artifactFor[DefaultRouting](service.routing), Nil, Map(), service.dialects)
      }, cluster.sla, Map(), cluster.dialects)
    }

    Deployment(uuid, clusters, bp.endpoints, Nil, bp.environmentVariables, Nil, Nil)
  }
}

trait DeploymentValidator {

  this: ArtifactSupport with FutureSupport with ActorSupport with NotificationProvider =>

  def validateBreeds: (Deployment => Deployment) = { (deployment: Deployment) =>
    val breeds = deployment.clusters.flatMap(_.services).filterNot(_.state.isInstanceOf[ReadyForUndeployment]).map(_.breed)

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 => error(NonUniqueBreedReferenceError(list.head))
    }

    val breedNames = breeds.map(_.name.toString).toSet
    breeds.foreach {
      breed => breed.dependencies.values.find(dependency => !breedNames.contains(dependency.name)).flatMap {
        dependency => error(UnresolvedDependencyError(breed, dependency))
      }
    }

    breeds.foreach(BreedReader.validateNonRecursiveDependencies)

    deployment
  }

  def validateRoutingWeights: (Deployment => Deployment) = { (deployment: Deployment) =>
    deployment.clusters.map(cluster => cluster -> weightOf(cluster.services)).find({
      case (cluster, weight) => weight != 100 && weight != 0
    }).flatMap({
      case (cluster, weight) => error(UnsupportedRoutingWeight(deployment, cluster, weight))
    })

    deployment
  }

  def validateScaleEscalations: (Deployment => Deployment) = { (deployment: Deployment) =>
    BlueprintReader.validateScaleEscalations(deployment)
    deployment
  }

  def validateEnvironmentVariables(blueprint: Blueprint, strictBreeds: Boolean) = blueprint match {
    case bp: AbstractBlueprint => bp.environmentVariables.find(ev => !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds)).flatMap {
      case t => error(UnresolvedEnvironmentVariableError(t.name, t.value))
    }
    case _ =>
  }

  def validateEndpoints(blueprint: Blueprint, strictBreeds: Boolean) = blueprint match {
    case bp: AbstractBlueprint => bp.environmentVariables.find(ev => !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds)).flatMap {
      case t => error(UnresolvedEndpointPortError(t.name, t.value))
    }
    case _ =>
  }

  def traitExists(blueprint: AbstractBlueprint, reference: Option[TraitReference], strictBreeds: Boolean): Boolean = reference match {
    case Some(TraitReference(cluster, group, local)) =>
      blueprint.clusters.find(_.name == cluster) match {
        case None => false
        case Some(c) => c.services.exists({
          service => service.breed match {
            case breed: DefaultBreed => breed.traitsFor(group).exists(_.name.toString == local)
            case _ => !strictBreeds
          }
        })
      }

    case _ => false
  }

  def weightOf(services: List[DeploymentService]) = services.flatMap(_.routing).flatMap(_.weight).sum
}

trait DeploymentMerger extends DeploymentTraitResolver {
  this: DeploymentValidator with ArtifactSupport with FutureSupport with ActorSupport with NotificationProvider =>

  def commit(create: Boolean, source: String): (Deployment => Deployment)

  def validate = { (deployment: Deployment) =>
    validateEnvironmentVariables(deployment, strictBreeds = true)
    validateEndpoints(deployment, strictBreeds = true)
    deployment
  }

  def resolveProperties = resolveParameters andThen resolveRouteMapping andThen validateEmptyVariables andThen resolveDependencyMapping

  def validateMerge = validateBreeds andThen validateRoutingWeights andThen validateScaleEscalations

  def merge(blueprint: Deployment): (Deployment => Deployment) = { (deployment: Deployment) =>

    val attachment = (validate andThen resolveProperties)(blueprint)

    val clusters = mergeClusters(deployment, attachment)
    val endpoints = mergeTrait(attachment.endpoints, deployment.endpoints)
    val ports = mergeTrait(attachment.ports, deployment.ports)
    val environmentVariables = mergeTrait(attachment.environmentVariables, deployment.environmentVariables)
    val constants = mergeTrait(attachment.constants, deployment.constants)
    val hosts = mergeTrait(attachment.hosts, deployment.hosts)

    validateMerge(Deployment(deployment.name, clusters, endpoints, ports, environmentVariables, constants, hosts))
  }

  def mergeTrait[A <: Trait](traits1: List[A], traits2: List[A]): List[A] =
    (traits1.map(t => t.name -> t).toMap ++ traits2.map(t => t.name -> t).toMap).values.toList

  def mergeClusters(stable: Deployment, blueprint: Deployment): List[DeploymentCluster] = {
    val deploymentClusters = stable.clusters.filter(cluster => !blueprint.clusters.exists(_.name == cluster.name))

    val blueprintClusters = blueprint.clusters.map { cluster =>
      stable.clusters.find(_.name == cluster.name) match {
        case None =>
          cluster.copy(services = mergeServices(stable, None, cluster))
        case Some(deploymentCluster) =>
          deploymentCluster.copy(services = mergeServices(stable, Some(deploymentCluster), cluster), routes = cluster.routes ++ deploymentCluster.routes, dialects = deploymentCluster.dialects ++ cluster.dialects, sla = cluster.sla)
      }
    }

    deploymentClusters ++ blueprintClusters
  }

  def mergeServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[DeploymentService] =
    mergeOldServices(deployment, stableCluster, blueprintCluster) ++ mergeNewServices(deployment, stableCluster, blueprintCluster)

  def mergeOldServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[DeploymentService] = stableCluster match {
    case None => Nil
    case Some(sc) =>
      sc.services.map { service =>
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None => service
          case Some(bpService) =>
            val scale = if (bpService.scale.isDefined) bpService.scale else service.scale
            val routing = if (bpService.routing.isDefined) bpService.routing else service.routing
            val state = if (service.scale != bpService.scale || service.routing != bpService.routing) ReadyForDeployment() else service.state

            service.copy(scale = scale, routing = routing, state = state, dialects = service.dialects ++ bpService.dialects)
        }
      }
  }

  def mergeNewServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[DeploymentService] = {
    val newServices = blueprintCluster.services.filter(service => stableCluster match {
      case None => true
      case Some(sc) => !sc.services.exists(_.breed.name == service.breed.name)
    })

    if (newServices.nonEmpty) {
      val oldWeight = stableCluster.flatMap(cluster => Some(cluster.services.flatMap({ service =>
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None => service.routing
          case Some(update) => update.routing
        }
      }).flatMap(_.weight).sum)) match {
        case None => 0
        case Some(sum) => sum
      }

      val newWeight = newServices.flatMap(_.routing).filter(_.isInstanceOf[DefaultRouting]).flatMap(_.weight).sum
      val availableWeight = 100 - oldWeight - newWeight

      if (availableWeight < 0)
        error(RoutingWeightError(blueprintCluster))

      val weight = Math.round(availableWeight / newServices.size)

      newServices.view.zipWithIndex.map({ case (service, index) =>
        val scale = service.scale match {
          case None =>
            implicit val timeout = DictionaryActor.timeout
            val key = DictionaryActor.containerScale.format(deployment.name, blueprintCluster.name, service.breed.name)
            offload(actorFor(DictionaryActor) ? DictionaryActor.Get(key)) match {
              case scale: DefaultScale => scale
              case e => error(UnresolvedEnvironmentValueError(key, e))
            }
          case Some(scale: DefaultScale) => scale
        }

        val defaultWeight = if (index == newServices.size - 1) availableWeight - index * weight else weight
        val routing = service.routing match {
          case None => Some(DefaultRouting("", Some(defaultWeight), Nil))
          case Some(r: DefaultRouting) => Some(r.copy(weight = Some(r.weight.getOrElse(defaultWeight))))
        }
        service.copy(scale = Some(scale), routing = routing)
      }).toList
    }
    else Nil
  }

  def resolveParameters: (Deployment => Deployment) = { (deployment: Deployment) =>
    implicit val timeout = DictionaryActor.timeout
    val host = offload(actorFor(DictionaryActor) ? DictionaryActor.Get(DictionaryActor.hostResolver)) match {
      case h: String => h
      case e => error(UnresolvedEnvironmentValueError(DictionaryActor.hostResolver, e))
    }

    val environmentVariables = deployment.clusters.flatMap({ cluster =>
      cluster.services.flatMap(service => service.breed.environmentVariables.filter(_.value.isDefined)).map(ev => {
        val name = TraitReference(cluster.name, TraitReference.EnvironmentVariables, ev.name).toString
        name -> ev.copy(name = name)
      })
    }).toMap ++ deployment.environmentVariables.map(ev => ev.name -> ev).toMap

    val constants = deployment.clusters.flatMap({ cluster =>
      cluster.services.flatMap(service => service.breed.constants.filter(_.value.isDefined)).map(constant => {
        val name = TraitReference(cluster.name, TraitReference.Constants, constant.name).toString
        name -> constant.copy(name = name)
      })
    }).toMap ++ deployment.constants.map(constant => constant.name -> constant).toMap

    val hosts = deployment.clusters.map(cluster => Host(TraitReference(cluster.name, TraitReference.Hosts, Host.host).toString, Some(host)))

    deployment.copy(environmentVariables = environmentVariables.values.toList, constants = constants.values.toList, hosts = hosts)
  }

  def resolveRouteMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
    deployment.copy(clusters = deployment.clusters.map({ cluster =>
      cluster.copy(routes = cluster.services.map(_.breed).flatMap(_.ports).map(_.number).map(port => cluster.routes.get(port) match {
        case None =>
          implicit val timeout = DictionaryActor.timeout
          val key = DictionaryActor.portAssignment.format(deployment.name, port)
          port -> (offload(actorFor(DictionaryActor) ? DictionaryActor.Get(key)) match {
            case number: Int => number
            case e => error(UnresolvedEnvironmentValueError(key, e))
          })
        case Some(number) => port -> number
      }).toMap)
    }))
  }

  def validateEmptyVariables: (Deployment => Deployment) = { (deployment: Deployment) =>

    deployment.clusters.flatMap({ cluster =>
      cluster.services.flatMap(service => {
        service.breed.ports.filter(_.value.isEmpty).map(port => {
          val name = TraitReference(cluster.name, TraitReference.Ports, port.name).toString
          deployment.environmentVariables.find(_.name == name).getOrElse(error(UnresolvedVariableValueError(service.breed, port.name)))
        })

        service.breed.environmentVariables.filter(_.value.isEmpty).map(environmentVariable => {
          val name = TraitReference(cluster.name, TraitReference.Ports, environmentVariable.name).toString
          deployment.environmentVariables.find(_.name == name).getOrElse(error(UnresolvedVariableValueError(service.breed, environmentVariable.name)))
        })
      })
    })

    deployment
  }

  def resolveDependencyMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
    val dependencies = deployment.clusters.flatMap(cluster => cluster.services.map(service => (service.breed.name, cluster.name))).toMap
    deployment.copy(clusters = deployment.clusters.map({ cluster =>
      cluster.copy(services = cluster.services.map({ service =>
        service.copy(dependencies = service.breed.dependencies.flatMap({ case (name, breed) =>
          dependencies.get(breed.name) match {
            case Some(d) => (name, d) :: Nil
            case None => Nil
          }
        }))
      }))
    }))
  }
}

trait DeploymentSlicer {
  this: DeploymentValidator with ArtifactSupport with FutureSupport with ActorSupport with NotificationProvider =>

  def validateRoutingWeightOfServicesForRemoval(deployment: Deployment, blueprint: Deployment) = deployment.clusters.foreach { cluster =>
    blueprint.clusters.find(_.name == cluster.name).foreach { bpc =>
      val weight = weightOf(cluster.services.filterNot(service => bpc.services.exists(_.breed.name == service.breed.name)))
      if (weight != 100 && weight != 0) error(InvalidRoutingWeight(deployment, cluster, weight))
    }
  }

  def commit(create: Boolean, source: String): (Deployment => Deployment)

  def slice(blueprint: Deployment): (Deployment => Deployment) = { (stable: Deployment) =>
    validateRoutingWeightOfServicesForRemoval(stable, blueprint)

    (validateBreeds andThen validateRoutingWeights andThen validateScaleEscalations)(stable.copy(clusters = stable.clusters.map(cluster =>
      blueprint.clusters.find(_.name == cluster.name) match {
        case None => cluster
        case Some(bpc) => cluster.copy(services = cluster.services.map(service => service.copy(state = if (bpc.services.exists(service.breed.name == _.breed.name)) ReadyForUndeployment() else service.state)))
      }
    ).filter(_.services.nonEmpty)))
  }
}

trait DeploymentUpdate {
  this: DeploymentValidator with ActorSupport with FutureSupport =>

  private implicit val timeout = PersistenceActor.timeout

  def updateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) = {
    val clusters = deployment.clusters.map(c => if (cluster.name == c.name) c.copy(sla = sla) else c)
    offload(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source)))
    sla
  }

  def updateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) = {
    lazy val services = cluster.services.map(s => if (s.breed.name == service.breed.name) service.copy(scale = Some(scale), state = ReadyForDeployment()) else s)
    val clusters = deployment.clusters.map(c => if (c.name == cluster.name) c.copy(services = services) else c)
    offload(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source)))
    scale
  }

  def updateRouting(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, routing: DefaultRouting, source: String) = {
    lazy val services = cluster.services.map(s => if (s.breed.name == service.breed.name) service.copy(routing = Some(routing), state = ReadyForDeployment()) else s)
    val clusters = deployment.clusters.map(c => if (c.name == cluster.name) c.copy(services = services) else c)
    offload(actorFor(PersistenceActor) ? PersistenceActor.Update(validateRoutingWeights(deployment.copy(clusters = clusters)), Some(source)))
    routing
  }
}

