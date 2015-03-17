package io.magnetic.vamp_core.operation.deployment

import java.util.UUID

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.dictionary.DictionaryActor
import _root_.io.magnetic.vamp_core.model.artifact.DeploymentService.{ReadyForDeployment, ReadyForUndeployment}
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.model.notification.{RoutingWeightError, UnresolvedEndpointPortError, UnresolvedParameterError}
import _root_.io.magnetic.vamp_core.model.reader.BreedReader
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentActor.{Create, DeploymentMessages, Merge, Slice}
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.Synchronize
import _root_.io.magnetic.vamp_core.operation.notification._
import _root_.io.magnetic.vamp_core.persistence.actor.{ArtifactSupport, PersistenceActor}
import _root_.io.magnetic.vamp_core.persistence.notification.PersistenceOperationFailure
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_common.notification.NotificationProvider

import scala.language.{existentials, postfixOps}

object DeploymentActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentActor]

  trait DeploymentMessages

  case class Create(blueprint: Blueprint) extends DeploymentMessages

  case class Merge(name: String, blueprint: Blueprint) extends DeploymentMessages

  case class Slice(name: String, blueprint: Blueprint) extends DeploymentMessages

}

class DeploymentActor extends Actor with ActorLogging with ActorSupport with BlueprintSupport with DeploymentMerger with DeploymentSlicer with ReplyActor with ArtifactSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = try {
    request match {
      case Create(blueprint) => merge(deploymentFor(), deploymentFor(blueprint))
      case Merge(name, blueprint) => merge(deploymentFor(name), deploymentFor(blueprint))
      case Slice(name, blueprint) => slice(deploymentFor(name), deploymentFor(blueprint))
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  def commit: (Deployment => Deployment) = { (deployment: Deployment) =>
    implicit val timeout: Timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment, create = true)) match {
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

  def deploymentFor(): Deployment = Deployment(uuid, List(), Nil, Map())

  def deploymentFor(name: String): Deployment = artifactFor[Deployment](name)

  def deploymentFor(blueprint: Blueprint): Deployment = {
    val bp = artifactFor[DefaultBlueprint](blueprint)

    val clusters = bp.clusters.map { cluster =>
      DeploymentCluster(cluster.name, cluster.services.map { service =>
        DeploymentService(ReadyForDeployment(), artifactFor[DefaultBreed](service.breed), artifactFor[DefaultScale](service.scale), artifactFor[DefaultRouting](service.routing), Nil)
      }, cluster.sla)
    }

    Deployment(uuid, clusters, bp.endpoints, bp.parameters)
  }
}

trait DeploymentMerger {
  this: ArtifactSupport with FutureSupport with ActorSupport with NotificationProvider =>

  def commit: (Deployment => Deployment)

  def validate = validateAndCollectParameters andThen validateEndpoints

  def resolveProperties = resolveParameters andThen resolveRouteMapping andThen resolveGlobalVariables andThen resolveDependencyMapping

  def validateMerge = validateBreeds andThen validateRoutingWeights

  def merge(deployment: Deployment, blueprint: Deployment): Deployment = {

    val appendage = (validate andThen resolveProperties)(blueprint)

    val clusters = mergeClusters(deployment, appendage)
    val endpoints = (appendage.endpoints ++ deployment.endpoints).distinct
    val parameters = appendage.parameters ++ deployment.parameters

    (validateMerge andThen commit)(Deployment(deployment.name, clusters, endpoints, parameters))
  }

  def mergeClusters(stable: Deployment, blueprint: Deployment): List[DeploymentCluster] = {
    val deploymentClusters = stable.clusters.filter(cluster => blueprint.clusters.find(_.name == cluster.name).isEmpty)

    val blueprintClusters = blueprint.clusters.map { cluster =>
      stable.clusters.find(_.name == cluster.name) match {
        case None =>
          cluster.copy(services = mergeServices(stable, None, cluster))
        case Some(deploymentCluster) =>
          deploymentCluster.copy(services = mergeServices(stable, Some(deploymentCluster), cluster), routes = cluster.routes ++ deploymentCluster.routes)
      }
    }

    deploymentClusters ++ blueprintClusters
  }

  def mergeServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[DeploymentService] = {
    val newServices = blueprintCluster.services.filter(service => stableCluster match {
      case None => true
      case Some(sc) => !sc.services.exists(_.breed.name == service.breed.name)
    })

    if (newServices.size > 0) {
      val oldWeight = stableCluster.flatMap(cluster => Some(cluster.services.map(_.routing).flatten.map(_.weight).flatten.sum)) match {
        case None => 0
        case Some(sum) => sum
      }
      val newWeight = newServices.map(_.routing).flatten.filter(_.isInstanceOf[DefaultRouting]).map(_.weight).flatten.sum
      val availableWeight = 100 - oldWeight - newWeight

      if (availableWeight < 0)
        error(RoutingWeightError(blueprintCluster))

      val weight = Math.round(availableWeight / newServices.size)

      (stableCluster match {
        case None => Nil
        case Some(sc) => sc.services
      }) ++ newServices.view.zipWithIndex.map({ case (service, index) =>

        val scale = service.scale match {
          case None =>
            implicit val timeout = DictionaryActor.timeout
            val key = DictionaryActor.containerScale.format(deployment.name, blueprintCluster.name, service.breed.name)
            offLoad(actorFor(DictionaryActor) ? DictionaryActor.Get(key)) match {
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

        DeploymentService(ReadyForDeployment(), service.breed, Some(scale), routing, Nil)
      })
    }
    else Nil
  }

  def validateAndCollectParameters: (Deployment => Deployment) = { (deployment: Deployment) =>
    deployment.parameters.find({
      case (Trait.Name(Some(scope), Some(group), port), _) =>
        deployment.clusters.find(_.name == scope) match {
          case None => true
          case Some(cluster) => cluster.services.find({
            service => service.breed match {
              case breed: DefaultBreed => breed.inTraits.exists(_.name.toString == port)
              case _ => false
            }
          }).isEmpty
        }
      case _ => true
    }).flatMap {
      case (name, value) => error(UnresolvedParameterError(name, value))
    }
    deployment
  }

  def validateEndpoints: (Deployment => Deployment) = { (deployment: Deployment) =>
    deployment.endpoints.map(port => port.name -> port.value).find({
      case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), port), _) =>
        deployment.clusters.find(_.name == scope) match {
          case None => true
          case Some(cluster) => cluster.services.find({
            service => service.breed match {
              case breed: DefaultBreed => breed.ports.exists(_.name.toString == port)
              case _ => false
            }
          }).isEmpty
        }
      case _ => true
    }).flatMap {
      case (name, value) => error(UnresolvedEndpointPortError(name, value))
    }
    deployment
  }

  def resolveParameters: (Deployment => Deployment) = { (deployment: Deployment) =>
    implicit val timeout = DictionaryActor.timeout
    val host = offLoad(actorFor(DictionaryActor) ? DictionaryActor.Get(DictionaryActor.hostResolver)) match {
      case h: String => h
      case e => error(UnresolvedEnvironmentValueError(DictionaryActor.hostResolver, e))
    }

    deployment.copy(parameters = deployment.clusters.flatMap({ cluster =>
      cluster.services.flatMap({ service =>
        val breed = service.breed
        breed.ports.filter(_.direction == Trait.Direction.Out).map(out => out.name.copy(scope = Some(cluster.name), group = Some(Trait.Name.Group.Ports)) -> out.value.get) ++
          breed.environmentVariables.filter(_.direction == Trait.Direction.Out).map(out => out.name.copy(scope = Some(cluster.name), group = Some(Trait.Name.Group.EnvironmentVariables)) -> out.value.get)
      })
    }).toMap ++ deployment.parameters ++ deployment.clusters.map(cluster => Trait.Name(Some(cluster.name), None, Trait.host) -> host))
  }

  def resolveRouteMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
    deployment.copy(clusters = deployment.clusters.map({ cluster =>
      cluster.copy(routes = cluster.services.map(_.breed).flatMap(_.ports).map(_.value.get).map(port => cluster.routes.get(port) match {
        case None =>
          implicit val timeout = DictionaryActor.timeout
          val key = DictionaryActor.portAssignment.format(deployment.name, port)
          port -> (offLoad(actorFor(DictionaryActor) ? DictionaryActor.Get(key)) match {
            case number: Int => number
            case e => error(UnresolvedEnvironmentValueError(key, e))
          })
        case Some(number) => port -> number
      }).toMap)
    }))
  }

  def resolveGlobalVariables: (Deployment => Deployment) = { (deployment: Deployment) =>

    def copyPort(breed: Breed, port: Port, targetScope: String, dependencyScope: String) = {
      port.name.copy(scope = Some(targetScope), group = Some(Trait.Name.Group.Ports)) -> (deployment.parameters.find({
        case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), value), _) if scope == dependencyScope && value == port.name.value => true
        case _ => false
      }) match {
        case None => error(UnresolvedVariableValueError(breed, port.name))
        case Some(parameter) => parameter._2
      })
    }

    def copyEnvironmentVariable(breed: Breed, ev: EnvironmentVariable, targetScope: String, dependencyScope: String) = {
      ev.name.copy(scope = Some(targetScope), group = Some(Trait.Name.Group.EnvironmentVariables)) -> (deployment.parameters.find({
        case (Trait.Name(Some(scope), Some(_), value), _) if scope == dependencyScope && value == ev.name.value => true
        case (Trait.Name(Some(scope), None, value), _) if scope == dependencyScope && value == ev.name.value && value == Trait.host => true
        case _ => false
      }) match {
        case None => error(UnresolvedVariableValueError(breed, ev.name))
        case Some(parameter) => parameter._2
      })
    }

    deployment.copy(parameters = deployment.clusters.flatMap(cluster => cluster.services.map(_.breed).flatMap({ breed =>
      breed.ports.filter(_.direction == Trait.Direction.In).flatMap({ port =>
        port.name.scope match {
          case None => copyPort(breed, port, cluster.name, cluster.name) :: Nil
          case _ => Nil
        }
      }) ++ breed.environmentVariables.filter(ev => ev.direction == Trait.Direction.In).flatMap({ ev =>
        ev.name.scope match {
          case None => copyEnvironmentVariable(breed, ev, cluster.name, cluster.name) :: Nil
          case _ => Nil
        }
      })
    })).toMap ++ deployment.parameters)
  }

  def resolveDependencyMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
    val dependencies = deployment.clusters.flatMap(cluster => cluster.services.map(service => (service.breed.name, cluster.name))).toMap
    deployment.copy(clusters = deployment.clusters.map({ cluster =>
      cluster.copy(services = cluster.services.map({ service =>
        service.copy(dependencies = service.breed.dependencies.map({ case (name, breed) =>
          (name, dependencies.get(breed.name).get)
        }).toMap)
      }))
    }))
  }

  def validateBreeds: (Deployment => Deployment) = { (deployment: Deployment) =>
    val breeds = deployment.clusters.flatMap(_.services).map(_.breed)

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
    def weight(cluster: DeploymentCluster) = cluster.services.map(_.routing).flatten.map(_.weight).flatten.sum
    deployment.clusters.find(cluster => weight(cluster) != 100).flatMap(cluster => error(UnsupportedRoutingWeight(deployment, cluster, weight(cluster))))
    deployment
  }
}

trait DeploymentSlicer {
  this: ArtifactSupport with FutureSupport with ActorSupport with NotificationProvider =>

  def commit: (Deployment => Deployment)

  def slice(stable: Deployment, blueprint: Deployment): Deployment = {
    // TODO validate cross dependencies
    commit(stable.copy(clusters = blueprint.clusters.map(cluster =>
      blueprint.clusters.find(_.name == cluster.name) match {
        case None => cluster
        case Some(bpc) => cluster.copy(services = cluster.services.filter(service => !bpc.services.exists(service.breed.name == _.breed.name)).map(service => service.copy(state = ReadyForUndeployment())))
      }
    ).filter(_.services.nonEmpty)))
  }
}

