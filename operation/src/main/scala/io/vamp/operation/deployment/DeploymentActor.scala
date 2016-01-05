package io.vamp.operation.deployment

import java.util.UUID

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.dictionary.DictionaryActor
import io.vamp.model.artifact.DeploymentService.State
import io.vamp.model.artifact.DeploymentService.State.Intention._
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader._
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.Synchronize
import io.vamp.operation.notification._
import io.vamp.persistence.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }

object DeploymentActor {

  trait DeploymentMessages

  case class Create(blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class Merge(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class Slice(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class UpdateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) extends DeploymentMessages

  case class UpdateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) extends DeploymentMessages

  case class UpdateRouting(deployment: Deployment, cluster: DeploymentCluster, routing: List[Gateway], source: String) extends DeploymentMessages

}

class DeploymentActor extends CommonSupportForActors with BlueprintSupport with DeploymentValidator with DeploymentMerger with DeploymentSlicer with DeploymentUpdate with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import DeploymentActor._

  def receive = {
    case Create(blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint)) andThen commit(source, validateOnly))(deploymentFor())
    }

    case Merge(name, blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint)) andThen commit(source, validateOnly))(deploymentFor(name, create = true))
    }

    case Slice(name, blueprint, source, validateOnly) ⇒ reply {
      (slice(deploymentFor(blueprint)) andThen commit(source, validateOnly))(deploymentFor(name))
    }

    case UpdateSla(deployment, cluster, sla, source) ⇒ reply {
      updateSla(deployment, cluster, sla, source)
    }

    case UpdateScale(deployment, cluster, service, scale, source) ⇒ reply {
      updateScale(deployment, cluster, service, scale, source)
    }

    case UpdateRouting(deployment, cluster, routing, source) ⇒ reply {
      updateRouting(deployment, cluster, routing, source)
    }

    case any ⇒ unsupported(UnsupportedDeploymentRequest(any))
  }

  def commit(source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Any]) = { future ⇒
    if (validateOnly) future
    else future.flatMap {
      case deployment ⇒
        implicit val timeout: Timeout = PersistenceActor.timeout
        checked[List[_]](IoC.actorFor[PersistenceActor] ? PersistenceActor.Update(deployment, Some(source))) map {
          case persisted ⇒
            IoC.actorFor[DeploymentSynchronizationActor] ! Synchronize(deployment)
            persisted
        }
    }
  }
}

trait BlueprintSupport extends DeploymentValidator with NameValidator with BlueprintRoutingHelper {
  this: ArtifactPaginationSupport with ArtifactSupport with ExecutionContextProvider with NotificationProvider ⇒

  private def uuid = UUID.randomUUID.toString

  def deploymentFor(): Future[Deployment] = Future(Deployment(uuid, Nil, Nil, Nil, Nil, Nil))

  def deploymentFor(name: String, create: Boolean = false): Future[Deployment] = {
    if (!create) {
      artifactFor[Deployment](name)
    } else {
      artifactForIfExists[Deployment](name) map {
        case Some(deployment) ⇒ deployment
        case None ⇒
          validateName(name)
          Deployment(name, clusters = Nil, gateways = Nil, ports = Nil, environmentVariables = Nil, hosts = Nil)
      }
    }
  }

  def deploymentFor(blueprint: Blueprint): Future[Deployment] = {
    artifactFor[DefaultBlueprint](blueprint) flatMap {
      case bp ⇒
        val clusters = bp.clusters.map { cluster ⇒
          for {
            services ← Future.traverse(cluster.services)({ service ⇒
              for {
                breed ← artifactFor[DefaultBreed](service.breed)
                scale ← artifactFor[DefaultScale](service.scale)
              } yield {
                DeploymentService(Deploy, breed, service.environmentVariables, scale, Nil, Map(), service.dialects)
              }
            })
            routing ← Future.sequence(cluster.routing map { r ⇒
              val routes: Future[List[Route]] = Future.sequence(r.routes.map({
                case route ⇒ artifactFor[DefaultRoute](route)
              }))

              routes.map(routes ⇒ r.copy(routes = routes))
            })

          } yield {
            DeploymentCluster(cluster.name, services, processAnonymousRouting(services, routing), cluster.sla, Map(), cluster.dialects)
          }
        }
        Future.sequence(clusters).map(Deployment(uuid, _, bp.gateways, Nil, bp.environmentVariables, Nil))
    }
  }
}

trait DeploymentValidator {
  this: BlueprintRoutingHelper with ArtifactPaginationSupport with ArtifactSupport with ExecutionContextProvider with NotificationProvider ⇒

  def validateServices: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    val services = deployment.clusters.flatMap(_.services).filterNot(_.state.intention == Undeploy)

    val breeds = services.map(_.breed)

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 ⇒ throwException(NonUniqueBreedReferenceError(list.head))
    }

    val breedNames = breeds.map(_.name.toString).toSet
    breeds.foreach {
      breed ⇒
        breed.dependencies.values.find(dependency ⇒ !breedNames.contains(dependency.name)).flatMap {
          dependency ⇒ throwException(UnresolvedDependencyError(breed, dependency))
        }
    }

    breeds.foreach(BreedReader.validateNonRecursiveDependencies)

    services.foreach { service ⇒
      service.environmentVariables.foreach { environmentVariable ⇒
        if (!service.breed.environmentVariables.exists(_.name == environmentVariable.name)) throwException(UnresolvedDependencyInTraitValueError(service.breed, environmentVariable.name))
      }
    }

    deployment
  }

  def validateRouting: (Deployment ⇒ Deployment) = validateRouteWeights andThen validateBlueprintGateways andThen validateRoutingAnonymousPortMapping

  def validateRouteWeights: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    deployment.clusters.map(cluster ⇒

      cluster -> weightOf(cluster, cluster.services, "")).find({
      case (cluster, weight) ⇒ weight != 100 && weight != 0
    }

    ).flatMap({
      case (cluster, weight) ⇒ throwException(UnsupportedRouteWeight(deployment, cluster, weight))
    })

    deployment
  }

  def validateScaleEscalations: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    BlueprintReader.validateScaleEscalations(deployment)
    deployment
  }

  def validateBlueprintEnvironmentVariables: (Future[Deployment] ⇒ Future[Deployment]) = { (futureBlueprint: Future[Deployment]) ⇒
    futureBlueprint.map {
      case bp: AbstractBlueprint ⇒
        bp.environmentVariables.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap {
          case t ⇒ throwException(UnresolvedEnvironmentVariableError(t.name, t.value.getOrElse("")))
        }.getOrElse(bp)
      case blueprint ⇒ blueprint
    }
  }

  def validateBlueprintRoutes: (Future[Deployment] ⇒ Future[Deployment]) = { (futureBlueprint: Future[Deployment]) ⇒
    // Reference check.
    futureBlueprint.map {
      case bp: AbstractBlueprint ⇒
        val ports = bp.gateways.flatMap { gateway ⇒
          gateway.routes.map { route ⇒
            if (route.length != 2) throwException(UnresolvedGatewayPortError(route.path.source, gateway.port.value.get))
            gateway.port.copy(name = TraitReference(route.path.segments.head, TraitReference.Ports, route.path.segments.tail.head).reference)
          }
        }

        ports.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap {
          case t ⇒ throwException(UnresolvedGatewayPortError(t.name, t.value))
        }.getOrElse(bp)
      case blueprint ⇒ blueprint
    }
    futureBlueprint
  }

  def validateGateways: (Deployment ⇒ Future[Deployment]) = { (deployment: Deployment) ⇒
    // Availability check.
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[Gateway] map {
      case gateways ⇒
        val ports = gateways.filter(gateway ⇒ GatewayPath(gateway.name).segments.head != deployment.name).map(gateway ⇒ gateway.port.number -> gateway).toMap

        deployment.gateways.foreach { gateway ⇒
          ports.get(gateway.port.number) match {
            case Some(g) ⇒ throwException(UnavailableGatewayPortError(gateway.port, g))
            case _       ⇒
          }
        }
        deployment
    }
  }

  def traitExists(blueprint: AbstractBlueprint, reference: Option[TraitReference], strictBreeds: Boolean): Boolean = reference match {
    case Some(TraitReference(cluster, group, local)) ⇒
      blueprint.clusters.find(_.name == cluster) match {
        case None ⇒ false
        case Some(c) ⇒ c.services.exists({
          service ⇒
            service.breed match {
              case breed: DefaultBreed ⇒ breed.traitsFor(group).exists(_.name.toString == local)
              case _                   ⇒ !strictBreeds
            }
        })
      }

    case _ ⇒ false
  }

  def weightOf(cluster: DeploymentCluster, services: List[DeploymentService], port: String): Int = cluster.routingBy(port).flatMap({ routing ⇒
    Some(routing.routes.filter({
      case route: DefaultRoute ⇒ services.exists(_.breed.name == route.path.normalized)
      case _                   ⇒ true
    }).map({
      case route: DefaultRoute ⇒ route.weight.getOrElse(Percentage(0)).value
      case route               ⇒ throwException(InternalServerError(s"unsupported route: $route"))
    }).sum)
  }).getOrElse(0)
}

trait DeploymentOperation {
  def commit(source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Any])
}

trait DeploymentMerger extends DeploymentOperation with DeploymentTraitResolver {
  this: DeploymentValidator with ArtifactSupport with ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def validateBlueprint = validateBlueprintEnvironmentVariables andThen validateBlueprintRoutes

  def resolveProperties = resolveHosts andThen resolveRouteMapping andThen validateEmptyVariables andThen resolveDependencyMapping

  def validateMerge = validateServices andThen validateRouting andThen validateScaleEscalations andThen validateGateways

  def merge(blueprint: Future[Deployment]): (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.flatMap {
      case deployment ⇒
        (validateBlueprint andThen resolveProperties)(blueprint) flatMap {
          case attachment ⇒
            mergeClusters(futureDeployment, attachment) flatMap {
              case clusters ⇒
                val gateways = mergeGateways(attachment, deployment)
                val ports = mergeTrait(attachment.ports, deployment.ports)
                val environmentVariables = mergeTrait(attachment.environmentVariables, deployment.environmentVariables)
                val hosts = mergeTrait(attachment.hosts, deployment.hosts)

                validateMerge(Deployment(deployment.name, clusters, gateways, ports, environmentVariables, hosts))
            }
        }
    }
  }

  def mergeGateways(blueprint: Deployment, deployment: Deployment): List[Gateway] = {
    blueprint.gateways.map(processGateway(deployment)) ++ deployment.gateways.filterNot(gateway ⇒ blueprint.gateways.exists(_.port.number == gateway.port.number))
  }

  def mergeTrait[A <: Trait](traits1: List[A], traits2: List[A]): List[A] =
    (traits1.map(t ⇒ t.name -> t).toMap ++ traits2.map(t ⇒ t.name -> t).toMap).values.toList

  def mergeClusters(futureStable: Future[Deployment], blueprint: Deployment): Future[List[DeploymentCluster]] = {
    futureStable.flatMap {
      case stable ⇒
        val deploymentClusters = stable.clusters.filter(cluster ⇒ !blueprint.clusters.exists(_.name == cluster.name)).map(Future(_))

        val blueprintClusters = blueprint.clusters.map { cluster ⇒
          stable.clusters.find(_.name == cluster.name) match {
            case None ⇒ mergeServices(stable, None, cluster).map {
              case services ⇒
                val nc = cluster.copy(services = services)
                nc.copy(routing = updatedWeights(stable, None, nc))
            }
            case Some(deploymentCluster) ⇒ mergeServices(stable, Some(deploymentCluster), cluster).map {
              case services ⇒
                val nc = deploymentCluster.copy(
                  services = services,
                  portMapping = cluster.portMapping ++ deploymentCluster.portMapping,
                  dialects = deploymentCluster.dialects ++ cluster.dialects,
                  routing = if (cluster.routing.nonEmpty) cluster.routing else deploymentCluster.routing,
                  sla = if (cluster.sla.isDefined) cluster.sla else deploymentCluster.sla
                )
                nc.copy(routing = updatedWeights(stable, Some(deploymentCluster), nc))
            }
          }
        }

        Future.sequence(deploymentClusters ++ blueprintClusters)
    }
  }

  def mergeServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): Future[List[DeploymentService]] =
    Future.sequence(mergeOldServices(deployment, stableCluster, blueprintCluster) ++ mergeNewServices(deployment, stableCluster, blueprintCluster))

  def mergeOldServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[Future[DeploymentService]] = stableCluster match {
    case None ⇒ Nil
    case Some(sc) ⇒ sc.services.map { service ⇒
      Future {
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None ⇒ service
          case Some(bpService) ⇒
            val scale = if (bpService.scale.isDefined) bpService.scale else service.scale
            val state: State = if (service.scale != bpService.scale || sc.routing != blueprintCluster.routing) Deploy else service.state

            service.copy(scale = scale, state = state, dialects = service.dialects ++ bpService.dialects)
        }
      }
    }
  }

  def mergeNewServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[Future[DeploymentService]] = {
    val (newServices, _) = newService(stableCluster, blueprintCluster)

    if (newServices.nonEmpty) {
      newServices.map {
        case service ⇒
          (service.scale match {
            case None ⇒
              implicit val timeout = DictionaryActor.timeout
              val key = DictionaryActor.containerScale.format(deployment.name, blueprintCluster.name, service.breed.name)
              actorFor[DictionaryActor] ? DictionaryActor.Get(key) map {
                case scale: DefaultScale ⇒ scale
                case e                   ⇒ throwException(UnresolvedEnvironmentValueError(key, e))
              }
            case Some(scale: DefaultScale) ⇒ Future(scale)
          }).map {
            case scale ⇒ service.copy(scale = Some(scale))
          }
      }
    } else Nil
  }

  def newService(stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): (List[DeploymentService], List[DeploymentService]) =
    blueprintCluster.services.partition(service ⇒ isNewService(stableCluster, service))

  def isNewService(stableCluster: Option[DeploymentCluster], blueprintService: DeploymentService) = stableCluster match {
    case None     ⇒ true
    case Some(sc) ⇒ !sc.services.exists(_.breed.name == blueprintService.breed.name)
  }

  def updatedWeights(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[Gateway] = {
    val (newServices, oldService) = newService(stableCluster, blueprintCluster)

    if (newServices.nonEmpty) {
      newServices.flatMap(_.breed.ports).map(_.name).toSet[String].map { portName ⇒

        val oldWeight = oldService.flatMap(s ⇒ blueprintCluster.route(s, portName)).flatMap(_.weight.map(_.value)).sum
        val newWeight = newServices.flatMap(s ⇒ blueprintCluster.route(s, portName)).flatMap(_.weight.map(_.value)).sum
        val availableWeight = 100 - oldWeight - newWeight

        if (availableWeight < 0)
          throwException(RouteWeightError(blueprintCluster))

        val weight = Math.round(availableWeight / newServices.size)

        val oldRoutes: List[Route] = oldService.flatMap { service ⇒
          blueprintCluster.route(service, portName) match {
            case None    ⇒ Nil
            case Some(r) ⇒ r :: Nil
          }
        }

        val newRoutes: List[Route] = newServices.view.zipWithIndex.toList.map {
          case (service, index) ⇒
            val defaultWeight = if (index == newServices.size - 1) availableWeight - index * weight else weight
            val route = blueprintCluster.route(service, portName) match {
              case None                  ⇒ DefaultRoute("", "", Option(Percentage(defaultWeight)), Nil)
              case Some(r: DefaultRoute) ⇒ r.copy(weight = Option(r.weight.getOrElse(Percentage(defaultWeight))))
            }
            route.copy(path = GatewayPath(service.breed.name :: Nil))
        }

        val port = Port(portName, None, None)
        val name = DeploymentCluster.gatewayNameFor(deployment, blueprintCluster, port)

        blueprintCluster.routingBy(portName).getOrElse(Gateway("", port, None, Nil)).copy(name = name, routes = oldRoutes ++ newRoutes)
      } toList

    } else stableCluster.map(_ ⇒ blueprintCluster.routing).getOrElse(Nil)
  }

  def processGateway(deployment: Deployment): Gateway ⇒ Gateway = processGatewayRoutes(deployment) andThen processGatewayWeights andThen updateGatewayName(deployment)

  def updateGatewayName(deployment: Deployment): Gateway ⇒ Gateway = { gateway ⇒
    gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway))
  }

  def processGatewayRoutes(deployment: Deployment): Gateway ⇒ Gateway = { gateway ⇒
    val routes = gateway.routes.map {
      case route: DefaultRoute   ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route: RouteReference ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route                 ⇒ route
    }
    gateway.copy(routes = routes)
  }

  def processGatewayWeights: Gateway ⇒ Gateway = { gateway ⇒

    gateway.routes.find(!_.isInstanceOf[DefaultRoute]).foreach(route ⇒ throwException(InternalServerError(s"unsupported gateway route: ${route.getClass}")))

    val (newRoutes, oldRoutes) = gateway.routes.map(_.asInstanceOf[DefaultRoute]).partition(_.weight.isEmpty)

    val routes = if (newRoutes.nonEmpty) {

      val oldWeight = oldRoutes.map(_.weight.get.value).sum
      val availableWeight = 100 - oldWeight

      if (availableWeight < 0)
        throwException(GatewayRouteWeightError(gateway))

      val weight = Math.round(availableWeight / newRoutes.size)

      val updatedRoutes = newRoutes.view.zipWithIndex.toList.map {
        case (route, index) ⇒
          val defaultWeight = if (index == newRoutes.size - 1) availableWeight - index * weight else weight
          route.copy(weight = Option(Percentage(defaultWeight)))
      }

      updatedRoutes ++ oldRoutes

    } else newRoutes ++ oldRoutes

    gateway.copy(routes = routes)
  }

  def resolveHosts: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.flatMap {
      case deployment ⇒
        implicit val timeout = DictionaryActor.timeout
        actorFor[DictionaryActor] ? DictionaryActor.Get(DictionaryActor.hostResolver) map {
          case host: String ⇒ deployment.copy(hosts = deployment.clusters.map(cluster ⇒ Host(TraitReference(cluster.name, TraitReference.Hosts, Host.host).toString, Some(host))))
          case e            ⇒ throwException(UnresolvedEnvironmentValueError(DictionaryActor.hostResolver, e))
        }
    }
  }

  def resolveRouteMapping: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case deployment ⇒
        val clusters = deployment.clusters.map { cluster ⇒
          val portMapping: Map[String, Int] = cluster.services.map(_.breed).flatMap(_.ports).map(port ⇒ cluster.portMapping.get(port.name) match {
            case None         ⇒ port.name -> 0
            case Some(number) ⇒ port.name -> number
          }).toMap
          cluster.copy(portMapping = portMapping)
        }
        deployment.copy(clusters = clusters)
    }
  }

  def validateEmptyVariables: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case deployment ⇒
        deployment.clusters.flatMap({ cluster ⇒
          cluster.services.flatMap(service ⇒ {
            service.breed.ports.filter(_.value.isEmpty).map(port ⇒ {
              val name = TraitReference(cluster.name, TraitReference.Ports, port.name).toString
              deployment.environmentVariables.find(_.name == name).getOrElse(throwException(UnresolvedVariableValueError(service.breed, port.name)))
            })

            service.breed.environmentVariables.filter(_.value.isEmpty).map(environmentVariable ⇒ {
              val name = TraitReference(cluster.name, TraitReference.EnvironmentVariables, environmentVariable.name).toString
              deployment.environmentVariables.find(_.name == name).getOrElse(throwException(UnresolvedVariableValueError(service.breed, environmentVariable.name)))
            })
          })
        })
        deployment
    }
  }

  def resolveDependencyMapping: (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.map {
      case deployment ⇒
        val dependencies = deployment.clusters.flatMap(cluster ⇒ cluster.services.map(service ⇒ (service.breed.name, cluster.name))).toMap
        deployment.copy(clusters = deployment.clusters.map({ cluster ⇒
          cluster.copy(services = cluster.services.map({ service ⇒
            service.copy(dependencies = service.breed.dependencies.flatMap({
              case (name, breed) ⇒
                dependencies.get(breed.name) match {
                  case Some(d) ⇒ (name, d) :: Nil
                  case None    ⇒ Nil
                }
            }))
          }))
        }))
    }
  }
}

trait DeploymentSlicer extends DeploymentOperation {
  this: DeploymentValidator with ArtifactSupport with ExecutionContextProvider with NotificationProvider ⇒

  def validateRoutingWeightOfServicesForRemoval(deployment: Deployment, blueprint: Deployment) = deployment.clusters.foreach { cluster ⇒
    blueprint.clusters.find(_.name == cluster.name).foreach { bpc ⇒
      val services = cluster.services.filterNot(service ⇒ bpc.services.exists(_.breed.name == service.breed.name))
      services.flatMap(_.breed.ports).map(_.name).toSet[String].foreach { port ⇒
        val weight = weightOf(cluster, services, port)
        if (weight != 100 && weight != 0) throwException(InvalidRouteWeight(deployment, cluster, weight))
      }
    }
  }

  def slice(futureBlueprint: Future[Deployment]): (Future[Deployment] ⇒ Future[Deployment]) = { (stableFuture: Future[Deployment]) ⇒
    for {
      blueprint ← futureBlueprint
      stable ← stableFuture
    } yield {
      validateRoutingWeightOfServicesForRemoval(stable, blueprint)

      (validateServices andThen validateRouting andThen validateScaleEscalations)(stable.copy(clusters = stable.clusters.map(cluster ⇒
        blueprint.clusters.find(_.name == cluster.name) match {
          case None      ⇒ cluster
          case Some(bpc) ⇒ cluster.copy(services = cluster.services.map(service ⇒ service.copy(state = if (bpc.services.exists(service.breed.name == _.breed.name)) Undeploy else service.state)))
        }
      ).filter(_.services.nonEmpty)))
    }
  }
}

trait DeploymentUpdate {
  this: DeploymentValidator with ActorSystemProvider ⇒

  private implicit val timeout = PersistenceActor.timeout

  def updateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) = {
    val clusters = deployment.clusters.map(c ⇒ if (cluster.name == c.name) c.copy(sla = sla) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source))
  }

  def updateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) = {
    lazy val services = cluster.services.map(s ⇒ if (s.breed.name == service.breed.name) service.copy(scale = Some(scale), state = Deploy) else s)
    val clusters = deployment.clusters.map(c ⇒ if (c.name == cluster.name) c.copy(services = services) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(deployment.copy(clusters = clusters), Some(source))
  }

  def updateRouting(deployment: Deployment, cluster: DeploymentCluster, routing: List[Gateway], source: String) = {
    val clusters = deployment.clusters.map(c ⇒ if (c.name == cluster.name) c.copy(routing = routing) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(validateRouting(deployment.copy(clusters = clusters)), Some(source))
  }
}
