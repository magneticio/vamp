package io.magnetic.vamp_core.operation.deployment

import java.util.UUID

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.dictionary.DictionaryActor
import _root_.io.magnetic.vamp_core.model.artifact.DeploymentService.{ReadyForDeployment, ReadyForUndeployment}
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.model.notification.{RoutingWeightError, UnresolvedEndpointPortError, UnresolvedParameterError}
import _root_.io.magnetic.vamp_core.model.reader.BreedReader
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentActor.{Create, Delete, DeploymentMessages, Update}
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.Synchronize
import _root_.io.magnetic.vamp_core.operation.notification._
import _root_.io.magnetic.vamp_core.persistence.actor.PersistenceActor
import _root_.io.magnetic.vamp_core.persistence.notification.ArtifactNotFound
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.language.{existentials, postfixOps}
import scala.reflect._

object DeploymentActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentActor]

  trait DeploymentMessages

  case class Create(blueprint: Blueprint) extends DeploymentMessages

  case class Update(name: String, blueprint: Blueprint) extends DeploymentMessages

  case class Delete(name: String, blueprint: Option[Blueprint] = None) extends DeploymentMessages

}

class DeploymentActor extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private def uuid = UUID.randomUUID.toString

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = try {
    request match {
      case Create(blueprint) => merge(Deployment(uuid, List(), Nil, Map()), asDefaultBlueprint(blueprint))
      case Update(name, blueprint) => merge(artifactFor[Deployment](name), asDefaultBlueprint(blueprint))
      case Delete(name, blueprint) => slice(artifactFor[Deployment](name), blueprint.flatMap { bp =>
        Some(asDefaultBlueprint(bp))
      })
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  private def artifactFor[T <: Artifact : ClassTag](name: String): T = {
    implicit val timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]])) match {
      case Some(artifact: T) => artifact
      case _ => error(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }

  private def asDefaultBlueprint(blueprint: Blueprint): DefaultBlueprint = blueprint match {
    case defaultBlueprint: DefaultBlueprint => defaultBlueprint
    case reference: BlueprintReference => artifactFor[Blueprint](reference.name).asInstanceOf[DefaultBlueprint]
  }

  private def merge(deployment: Deployment, blueprint: DefaultBlueprint): Any = {
    val clusters = mergeClusters(deployment, blueprint)
    val endpoints = (blueprint.endpoints ++ deployment.endpoints).distinct
    val parameters = blueprint.parameters ++ deployment.parameters

    (validateParameters andThen collectParameters andThen validateAll andThen resolveParameters andThen commit)(Deployment(deployment.name, clusters, endpoints, parameters))
  }

  private def mergeClusters(deployment: Deployment, blueprint: DefaultBlueprint): List[DeploymentCluster] = {
    val deploymentClusters = deployment.clusters.filter(cluster => blueprint.clusters.find(_.name == cluster.name).isEmpty)

    val blueprintClusters = blueprint.clusters.map { cluster =>
      deployment.clusters.find(_.name == cluster.name) match {
        case None =>
          val dc = DeploymentCluster(cluster.name, Nil, cluster.sla)
          dc.copy(services = mergeServices(deployment, dc, cluster))
        case Some(deploymentCluster) =>
          deploymentCluster.copy(services = mergeServices(deployment, deploymentCluster, cluster))
      }
    }

    deploymentClusters ++ blueprintClusters
  }

  private def mergeServices(deployment: Deployment, deploymentCluster: DeploymentCluster, cluster: Cluster): List[DeploymentService] = {
    val newServices = cluster.services.filter(service => !deploymentCluster.services.exists(_.breed.name == service.breed.name))

    if (newServices.size > 0) {
      val oldWeight = deploymentCluster.services.map(_.routing.weight).flatten.sum
      val newWeight = newServices.map(_.routing).flatten.filter(_.isInstanceOf[DefaultRouting]).map(_.asInstanceOf[DefaultRouting]).map(_.weight).flatten.sum
      val availableWeight = 100 - oldWeight - newWeight

      if (availableWeight < 0)
        error(RoutingWeightError(cluster))

      val weight = Math.round(availableWeight / newServices.size)

      deploymentCluster.services ++ newServices.view.zipWithIndex.map({ case (service, index) =>

        val breed = service.breed match {
          case b: DefaultBreed => b
          case b: Breed => artifactFor[Breed](b.name).asInstanceOf[DefaultBreed]
        }

        val scale = service.scale match {
          case None =>
            implicit val timeout = DictionaryActor.timeout
            val key = DictionaryActor.containerScale.format(deployment.name, deploymentCluster.name, service.breed.name)
            offLoad(actorFor(DictionaryActor) ? DictionaryActor.Get(key)) match {
              case scale: DefaultScale => scale
              case e => error(UnresolvedEnvironmentValueError(key, e))
            }
          case Some(scale: DefaultScale) => scale
          case Some(scale: Scale) => artifactFor[Scale](scale.name).asInstanceOf[DefaultScale]
        }

        val routing = service.routing match {
          case None => DefaultRouting("", Some(if (index == newServices.size - 1) availableWeight - index * weight else weight), Nil)
          case Some(routing: DefaultRouting) => routing
          case Some(routing: Routing) => artifactFor[Routing](routing.name).asInstanceOf[DefaultRouting]
        }

        DeploymentService(ReadyForDeployment(), breed, scale, routing, Nil)
      })
    }
    else Nil
  }

  private def slice(deployment: Deployment, blueprint: Option[DefaultBlueprint]): Any = blueprint match {
    // TODO validation
    case None =>
      commit(deployment.copy(clusters = deployment.clusters.map({ cluster =>
        cluster.copy(services = cluster.services.map(service => service.copy(state = ReadyForUndeployment())))
      })))

    case Some(bp) =>
      commit(deployment.copy(clusters = deployment.clusters.map(cluster =>
        bp.clusters.find(_.name == cluster.name) match {
          case None => cluster
          case Some(bpc) => cluster.copy(services = cluster.services.filter(service => !bpc.services.exists(service.breed.name == _.breed.name)).map(service => service.copy(state = ReadyForUndeployment())))
        }
      ).filter(_.services.nonEmpty)))
  }

  private def commit(deployment: Deployment): Any = {
    persist(deployment) match {
      case persisted: Deployment =>
        actorFor(DeploymentSynchronizationActor) ! Synchronize(persisted)
        persisted
      case any => any
    }
  }

  private def collectParameters: (Deployment => Deployment) = { (deployment: Deployment) =>
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

  private def validateAll: (Deployment => Deployment) =
    validateBreeds andThen validateEndpoints andThen validateRoutingWeights

  private def validateBreeds: (Deployment => Deployment) = { (deployment: Deployment) =>
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

  private def validateEndpoints: (Deployment => Deployment) = { (deployment: Deployment) =>
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

  private def validateRoutingWeights: (Deployment => Deployment) = { (deployment: Deployment) =>
    def weight(cluster: DeploymentCluster) = cluster.services.map(_.routing.weight).flatten.sum
    deployment.clusters.find(cluster => weight(cluster) != 100).flatMap(cluster => error(UnsupportedRoutingWeight(deployment, cluster, weight(cluster))))
    deployment
  }

  private def validateParameters: (Deployment => Deployment) = { (deployment: Deployment) =>
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

  private def resolveParameters: (Deployment => Deployment) =
    resolveRouteMapping andThen resolveGlobalVariables andThen resolveDependencyMapping

  private def resolveRouteMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
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

  private def resolveGlobalVariables: (Deployment => Deployment) = { (deployment: Deployment) =>

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

  private def resolveDependencyMapping: (Deployment => Deployment) = { (deployment: Deployment) =>
    val dependencies = deployment.clusters.flatMap(cluster => cluster.services.map(service => (service.breed.name, cluster.name))).toMap
    deployment.copy(clusters = deployment.clusters.map({ cluster =>
      cluster.copy(services = cluster.services.map({ service =>
        service.copy(dependencies = service.breed.dependencies.map({ case (name, breed) =>
          (name, dependencies.get(breed.name).get)
        }).toMap)
      }))
    }))
  }

  private def persist(deployment: Deployment): Any = {
    implicit val timeout: Timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment, create = true))
  }
}

