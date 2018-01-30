package io.vamp.operation.deployment

import akka.pattern.ask
import akka.util.Timeout
import akka.actor.ActorSystem
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common._
import io.vamp.model.artifact.DeploymentService.Status.Intention
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader._
import io.vamp.model.resolver.DeploymentValueResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.Synchronize
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification._
import io.vamp.persistence._
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.refactor.exceptions.{ GeneralPersistenceError, PersistenceTypeError }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.Try

object DeploymentActor {

  val gatewayHost = Config.string("vamp.gateway-driver.host")

  def defaultScale()(implicit namespace: Namespace) = DefaultScale(
    Quantity.of(Config.double("vamp.operation.deployment.scale.cpu")()),
    MegaByte.of(Config.string("vamp.operation.deployment.scale.memory")()),
    Config.int("vamp.operation.deployment.scale.instances")()
  )

  def defaultArguments()(implicit namespace: Namespace) = Config.stringList("vamp.operation.deployment.arguments")().map(Argument(_))

  private val deploymentActorrMap: scala.collection.mutable.Map[String, DeploymentActor] =
    new scala.collection.concurrent.TrieMap[String, DeploymentActor]()

  // TODO: Make only creation of DAO synchronized. Retrieval can be left non-Sync
  def apply()(implicit ns: Namespace, as: ActorSystem): DeploymentActor = this.synchronized {
    deploymentActorrMap.getOrElseUpdate(ns.name, createDeploymentActor)
  }

  private def createDeploymentActor(implicit ns: Namespace, as: ActorSystem): DeploymentActor = {
    new DeploymentActor()
  }
}

class DeploymentActor(implicit val actorSystem: ActorSystem, val namespace: Namespace)
    extends BlueprintSupport
    with DeploymentValidator
    with DeploymentMerger
    with DeploymentSlicer with CommonProvider
    with OperationNotificationProvider {

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  def create(blueprint: Blueprint, source: String, validateOnly: Boolean): Future[Deployment] = {
    for {
      deployment ← deploymentFor(blueprint.name)
      deploymentForBlueprint ← deploymentForBlueprint(blueprint)
      afterMerge ← doMerge(deploymentForBlueprint, deployment, validateOnly)
      _ ← if (!validateOnly) createOrUpdate(afterMerge) else Future.successful(UnitPlaceholder)
    } yield afterMerge
  }

  def merge(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean): Future[Deployment] = {
    for {
      deployment ← deploymentFor(name)
      deploymentForBlueprint ← deploymentForBlueprint(blueprint)
      afterMerge ← doMerge(deploymentForBlueprint, deployment, validateOnly)
      _ ← if (!validateOnly) createOrUpdate(afterMerge) else Future.successful(UnitPlaceholder)
    } yield afterMerge
  }

  def slice(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean): Future[UnitPlaceholder] = {
    for {
      deployment ← VampPersistence().read[Deployment](Id[Deployment](name))
      deploymentForBlueprint ← deploymentForBlueprint(blueprint)
      afterSlice ← doSlice(deploymentForBlueprint, deployment, validateOnly)
      _ ← if (!validateOnly) createOrUpdate(afterSlice) else Future.successful(UnitPlaceholder)
    } yield UnitPlaceholder
  }

  def updateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla]): Future[UnitPlaceholder] = {
    val clusters = deployment.clusters.map(c ⇒ if (cluster.name == c.name) c.copy(sla = sla) else c)
    VampPersistence().update[Deployment](deploymentSerilizationSpecifier.idExtractor(deployment), (d: Deployment) ⇒ d.copy(clusters = clusters)).map(_ ⇒ UnitPlaceholder)
  }

  def updateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String, validateOnly: Boolean): Future[UnitPlaceholder] = {
    if (cluster.services.find(_.breed.name == service.breed.name).isDefined) {
      for {
        _ ← DeploymentPersistenceOperations.updateServiceScale(deployment, cluster, service, scale, source)
        _ ← DeploymentPersistenceOperations.updateServiceStatus(deployment, cluster, service, Intention.Deployment)
      } yield UnitPlaceholder
    }
    else Future.successful(UnitPlaceholder)
  }

  private def createOrUpdate(deployment: Deployment): Future[UnitPlaceholder] = {
    for {
      objectExists ← VampPersistence().readIfAvailable[Deployment](deploymentSerilizationSpecifier.idExtractor(deployment)).map(_.isDefined)
      _ ← if (objectExists) VampPersistence().update[Deployment](deploymentSerilizationSpecifier.idExtractor(deployment), _ ⇒ deployment)
      else VampPersistence().create[Deployment](deployment)
    } yield {
      IoC.actorFor[DeploymentSynchronizationActor] ! Synchronize(deployment)
      UnitPlaceholder
    }
  }

  private def deploymentFor(name: String): Future[Deployment] = {
    for {
      existingDeployment ← VampPersistence().readIfAvailable[Deployment](Id[Deployment](name))
      deployment ← existingDeployment match {
        case Some(x) ⇒ Future.successful(x)
        case None ⇒ {
          VampPersistence().readIfAvailable[Workflow](Id[Workflow](name)).map {
            case Some(_) ⇒ throwException(DeploymentWorkflowNameCollision(name))
            case _ ⇒ {
              validateName(name)
              Deployment(name, RootAnyMap.empty, clusters = Nil, gateways = Nil, ports = Nil, environmentVariables = Nil, hosts = Nil)
            }
          }
        }
      }
    } yield deployment
  }
}

trait BlueprintSupport extends DeploymentValidator with NameValidator with BlueprintGatewayHelper with ArtifactExpansionSupport {
  this: CommonProvider with VampJsonFormats ⇒

  def deploymentForBlueprint(blueprint: Blueprint): Future[Deployment] = {
    val bp = if (blueprint.isInstanceOf[DefaultBlueprint]) blueprint.asInstanceOf[DefaultBlueprint]
    else throw PersistenceTypeError(objectId = Id[Blueprint](blueprint.name), objectTypeName = blueprint.getClass.getName, desiredTypeName = "DefaultBlueprint")
    val clusters = bp.clusters.map { cluster ⇒
      for {
        services ← Future.traverse(cluster.services)({ service ⇒
          for {
            breed ← VampPersistence().read[Breed](Id[Breed](service.breed.name))
            defaultBreed = if (breed.isInstanceOf[DefaultBreed]) breed.asInstanceOf[DefaultBreed] else throw PersistenceTypeError(Id[Breed](breed.name), breed.getClass.getName, "DefaultBreed")
            defaultScale = service.scale match {
              case None ⇒ None
              case Some(s) ⇒ if (s.isInstanceOf[DefaultScale]) Some(s.asInstanceOf[DefaultScale])
              else throw GeneralPersistenceError(Id[DefaultBlueprint](bp.name), s"The internal scale object ${service.scale} is of type ${service.scale.getClass} instead of the expected DefaultScale")
            }
          } yield {
            DeploymentService(Intention.Deployment, defaultBreed, service.environmentVariables, defaultScale, Nil, arguments(defaultBreed, service), service.healthChecks, service.network, Map(), service.dialects)
          }
        })
        gateways ← expandGateways(cluster.gateways)

      } yield {
        DeploymentCluster(cluster.name, cluster.metadata, services, processAnonymousInternalGateways(services, gateways), cluster.healthChecks, cluster.network, cluster.sla, cluster.dialects)
      }
    }

    for {
      c ← Future.sequence(clusters)
      g ← expandGateways(bp.gateways)
    } yield {
      Deployment(blueprint.name, blueprint.metadata, c, g, Nil, bp.environmentVariables, Nil, bp.dialects)
    }
  }

  private def arguments(breed: DefaultBreed, service: Service): List[Argument] = {
    val all = DeploymentActor.defaultArguments() ++ breed.arguments ++ service.arguments

    val (privileged, others) = all.partition(_.privileged)

    privileged.lastOption.map(_ :: others).getOrElse(others)
    DeploymentActor.defaultArguments() ++ breed.arguments ++ service.arguments
  }
}

trait DeploymentValidator extends VampJsonFormats with DeploymentValueResolver {
  this: BlueprintGatewayHelper with ArtifactSupport with CommonProvider ⇒

  def validateServices(deployment: Deployment): UnitPlaceholder = {
    val services = deployment.clusters.flatMap(_.services).filterNot(_.status.intention == Intention.Undeployment)

    val breeds = services.map(_.breed)

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 ⇒ throwException(NonUniqueBreedReferenceError(list.head))
    }

    breeds.foreach { breed ⇒
      breed.dependencies.values.find { dependency ⇒
        !breeds.filterNot(_.name == breed.name).exists(matchDependency(dependency))
      } flatMap { dependency ⇒
        throwException(UnresolvedDependencyError(breed, dependency))
      }
    }

    breeds.foreach(BreedReader.validateNonRecursiveDependencies)

    services.foreach { service ⇒
      service.environmentVariables.foreach { environmentVariable ⇒
        if (!service.breed.environmentVariables.exists(_.name == environmentVariable.name)) throwException(UnresolvedDependencyInTraitValueError(service.breed, environmentVariable.name))
      }
    }
    UnitPlaceholder
  }

  def validateInternalGateways(deployment: Deployment): UnitPlaceholder = {
    validateRouteWeights(deployment)
    validateBlueprintGateways(deployment)
    validateInternalGatewayAnonymousPortMapping(deployment)
    UnitPlaceholder
  }

  def validateRouteWeights(deployment: Deployment): UnitPlaceholder = {
    deployment.clusters.map(cluster ⇒

      cluster → weightOf(cluster, cluster.services, "")).find({
      case (cluster, weight) ⇒ weight != 100 && weight != 0
    }).flatMap({
      case (cluster, weight) ⇒ throwException(UnsupportedRouteWeight(deployment, cluster, weight))
    })
    UnitPlaceholder
  }

  def validateScaleEscalations(deployment: Deployment): UnitPlaceholder = {
    BlueprintReader.validateScaleEscalations(deployment)
    UnitPlaceholder
  }

  def validateBlueprintEnvironmentVariables(deloyment: Deployment): UnitPlaceholder = {
    deloyment match {
      case bp: AbstractBlueprint ⇒
        bp.environmentVariables.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap { t ⇒
          throwException(UnresolvedEnvironmentVariableError(t.name, t.value.getOrElse("")))
        }.getOrElse(UnitPlaceholder)
      case blueprint ⇒ UnitPlaceholder
    }
  }

  def validateBlueprintRoutes(deployment: Deployment): UnitPlaceholder = {
    deployment match {
      case bp: AbstractBlueprint ⇒
        val ports = bp.gateways.flatMap { gateway ⇒
          gateway.routes.map { route ⇒
            if (route.length != 2) throwException(UnresolvedGatewayPortError(route.path.source, gateway.port.value.get))
            gateway.port.copy(name = TraitReference(route.path.segments.head, TraitReference.Ports, route.path.segments.tail.head).reference)
          }
        }
        ports.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap { t ⇒
          throwException(UnresolvedGatewayPortError(t.name, t.value))
        }.getOrElse(UnitPlaceholder)
      case blueprint ⇒ UnitPlaceholder
    }
  }

  def validateGateways(deployment: Deployment): Future[UnitPlaceholder] = {
    // Availability check.
    VampPersistence().getAll[Gateway]() map { gatewayResult ⇒
      val gateways = gatewayResult.response
      val otherGateways = gateways.filter(gateway ⇒ GatewayPath(gateway.name).segments.head != deployment.name)

      deployment.gateways.map { gateway ⇒
        otherGateways.find(_.port.number == gateway.port.number) match {
          case Some(g) ⇒ throwException(UnavailableGatewayPortError(gateway.port, g))
          case _       ⇒ gateway
        }
      }
      UnitPlaceholder
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

  def weightOf(cluster: DeploymentCluster, services: List[DeploymentService], port: String): Int = cluster.gatewayBy(port).flatMap({ routing ⇒
    Some(routing.routes.filter({
      case route: DefaultRoute ⇒ route.path.segments match {
        case _ :: _ :: s :: _ :: Nil ⇒ services.exists { service ⇒ service.breed.name == s }
        case _                       ⇒ true
      }
      case _ ⇒ true
    }).map({
      case route: DefaultRoute ⇒ route.weight.getOrElse(Percentage(0)).value
      case route               ⇒ throwException(InternalServerError(s"unsupported route: $route"))
    }).sum)
  }).getOrElse(0)
}

trait DeploymentGatewayOperation {
  this: CommonProvider ⇒

  def serviceRoutePath(deployment: Deployment, cluster: DeploymentCluster, serviceName: String, portName: String) = GatewayPath(deployment.name :: cluster.name :: serviceName :: portName :: Nil)

  def updateRoutePaths(deployment: Deployment, cluster: DeploymentCluster, gateway: Gateway): Gateway = {
    gateway.copy(
      name = GatewayPath(deployment.name :: cluster.name :: gateway.port.name :: Nil).normalized,
      routes = gateway.routes.map {
        case route: DefaultRoute if route.length == 1 ⇒ route.copy(path = serviceRoutePath(deployment, cluster, route.path.normalized, gateway.port.name))
        case route if route.length == 4               ⇒ route
        case route                                    ⇒ throwException(InternalServerError(s"unsupported cluster route: ${route.length}"))
      }
    )
  }

  def resetServiceArtifacts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, state: DeploymentService.Status = Intention.Deployment): Future[Unit] = {
    for {
      _ ← DeploymentPersistenceOperations.updateServiceStatus(deployment, cluster, service, state)
      _ ← DeploymentPersistenceOperations.resetDeploymentService(deployment, cluster, service)
      _ ← DeploymentPersistenceOperations.resetGateway(deployment, cluster, service) // TODO: which gateway should be removed?
    } yield ()
  }

  def resetInternalRouteArtifacts(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Future[Unit] = {
    DeploymentPersistenceOperations.resetInternalRouteArtifacts(deployment, cluster, service)
  }
}

trait DeploymentMerger extends DeploymentValueResolver with DeploymentGatewayOperation {
  this: DeploymentValidator with ArtifactSupport with CommonProvider ⇒

  def validateBlueprint(deployment: Deployment): Future[UnitPlaceholder] = Future.fromTry(Try {
    validateBlueprintEnvironmentVariables(deployment); (validateBlueprintRoutes(deployment))
  })

  def resolveProperties(deployment: Deployment): Future[Deployment] = Future.fromTry(Try {
    val resolved = resolveDependencyMapping(resolveHosts(deployment))
    validateEmptyVariables(resolved)
    resolved
  })

  //andThen validateEmptyVariables andThen resolveDependencyMapping

  def validateMerge(deployment: Deployment) = validateGateways(deployment).map { _ ⇒ validateServices(deployment); validateInternalGateways(deployment); validateScaleEscalations(deployment) }

  def doMerge(deploymentForBlueprint: Deployment, deployment: Deployment, validateOnly: Boolean): Future[Deployment] = {
    for {
      _ ← validateBlueprint(deploymentForBlueprint)
      attachment ← resolveProperties(deploymentForBlueprint)
      result ← mergeClusters(deployment, attachment, validateOnly) flatMap { clusters ⇒
        val gateways = mergeGateways(attachment, deployment)
        val ports = mergeTrait(attachment.ports, deployment.ports)
        val environmentVariables = mergeTrait(attachment.environmentVariables, deployment.environmentVariables)
        val hosts = mergeTrait(attachment.hosts, deployment.hosts)
        val metadata = RootAnyMap(deployment.metadata.rootMap ++ attachment.metadata.rootMap)
        val dialects = RootAnyMap(deployment.dialects.rootMap ++ attachment.dialects.rootMap)
        val newDeployment = Deployment(deployment.name, metadata, clusters, gateways, ports, environmentVariables, hosts, dialects)
        validateMerge(newDeployment) flatMap { _ ⇒
          implicit val timeout = GatewayActor.timeout
          Future.sequence {
            gateways.map { gateway ⇒
              if (newDeployment.gateways.exists(_.name == gateway.name))
                actorFor[GatewayActor] ? GatewayActor.Update(gateway, None, validateOnly = validateOnly, promote = true, force = true)
              else
                actorFor[GatewayActor] ? GatewayActor.Create(gateway, None, validateOnly = validateOnly, force = true)
            }
          } map (_ ⇒ newDeployment)
        }
      }
    } yield result
  }

  def mergeGateways(blueprint: Deployment, deployment: Deployment): List[Gateway] = {
    blueprint.gateways.map(processGateway(deployment)) ++ deployment.gateways.filterNot(gateway ⇒ blueprint.gateways.exists(_.port.number == gateway.port.number))
  }

  def mergeTrait[A <: Trait](traits1: List[A], traits2: List[A]): List[A] =
    (traits1.map(t ⇒ t.name → t).toMap ++ traits2.map(t ⇒ t.name → t).toMap).values.toList

  def mergeClusters(stable: Deployment, blueprint: Deployment, validateOnly: Boolean): Future[List[DeploymentCluster]] = {
    val deploymentClusters = stable.clusters.filter(cluster ⇒ !blueprint.clusters.exists(_.name == cluster.name)).map(Future(_))

    val blueprintClusters = blueprint.clusters.map { cluster ⇒
      stable.clusters.find(_.name == cluster.name) match {
        case None ⇒
          mergeServices(stable, None, cluster, validateOnly).flatMap { services ⇒
            val nc = cluster.copy(services = services)
            updatedRouting(stable, None, nc, validateOnly) map {
              routing ⇒ nc.copy(gateways = routing)
            }
          }
        case Some(deploymentCluster) ⇒
          mergeServices(stable, Some(deploymentCluster), cluster, validateOnly).flatMap { services ⇒
            val nc = deploymentCluster.copy(
              services = services,
              dialects = RootAnyMap(deploymentCluster.dialects.rootMap ++ cluster.dialects.rootMap),
              gateways = if (cluster.gateways.nonEmpty) cluster.gateways else deploymentCluster.gateways,
              sla = if (cluster.sla.isDefined) cluster.sla else deploymentCluster.sla
            )
            updatedRouting(stable, Some(deploymentCluster), nc, validateOnly) map {
              routing ⇒ nc.copy(gateways = routing)
            }
          }
      }
    }
    Future.sequence(deploymentClusters ++ blueprintClusters)
  }

  def mergeServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): Future[List[DeploymentService]] = {
    Future.sequence {
      mergeOldServices(deployment, stableCluster, blueprintCluster, validateOnly) ++ mergeNewServices(deployment, stableCluster, blueprintCluster, validateOnly)
    }
  }

  def mergeOldServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): List[Future[DeploymentService]] = stableCluster match {
    case None ⇒ Nil
    case Some(sc) ⇒ sc.services.map { service ⇒
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None ⇒ Future.successful(service)
          case Some(bpService) ⇒ {

            val scale = if (bpService.scale.isDefined) bpService.scale else service.scale
            val state: DeploymentService.Status =
              if (service.scale != bpService.scale || sc.gateways != blueprintCluster.gateways) Intention.Deployment
              else service.status

            for {
              _ <- if (!validateOnly) resetServiceArtifacts(deployment, blueprintCluster, service, state) else Future.successful()
            } yield
              service.copy(
                scale = scale,
                dialects = RootAnyMap(service.dialects.rootMap ++ bpService.dialects.rootMap),
                healthChecks = bpService.healthChecks
              )
          }
        }
    }
  }

  def mergeNewServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): List[Future[DeploymentService]] = {
    val (newServices, _) = newService(stableCluster, blueprintCluster)

    if (newServices.nonEmpty) {
      newServices.map { service ⇒
        for {
          _ <- if (!validateOnly) resetServiceArtifacts(deployment, blueprintCluster, service) else Future.successful()

        } yield {
          val scale = service.scale match {
            case None                      ⇒ DeploymentActor.defaultScale()
            case Some(scale: DefaultScale) ⇒ scale
          }
          service.copy(scale = Some(scale))
        }
      }
    }
    else Nil
  }

  def newService(stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): (List[DeploymentService], List[DeploymentService]) =
    blueprintCluster.services.partition(service ⇒ isNewService(stableCluster, service))

  def isNewService(stableCluster: Option[DeploymentCluster], blueprintService: DeploymentService) = stableCluster match {
    case None     ⇒ true
    case Some(sc) ⇒ !sc.services.exists(_.breed.name == blueprintService.breed.name)
  }

  def updatedRouting(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster, validateOnly: Boolean): Future[List[Gateway]] = {

    def exists(gateway: Gateway) = stableCluster.exists(cluster ⇒ cluster.gateways.exists(_.port.name == gateway.port.name))

    implicit val timeout = Timeout(5.second)
    val routings = resolveRoutings(deployment, blueprintCluster)
    val promote = stableCluster.exists(cluster ⇒ cluster.gateways.size == blueprintCluster.gateways.size)

    Future.sequence {
      routings.map { gateway ⇒
        if (exists(gateway))
          (IoC.actorFor[GatewayActor] ? GatewayActor.Update(updateRoutePaths(deployment, blueprintCluster, gateway), None, validateOnly = validateOnly, promote = promote, force = true)).map(x ⇒
            if (x.isInstanceOf[Gateway]) x.asInstanceOf[Gateway] else throwException(InternalServerError(s"Cannot Update Gateway for deployment : $deployment"))
          )
        else
          (IoC.actorFor[GatewayActor] ? GatewayActor.Create(updateRoutePaths(deployment, blueprintCluster, gateway), None, validateOnly = validateOnly, force = true)).map(x ⇒
            if (x.isInstanceOf[Gateway]) x.asInstanceOf[Gateway] else throwException(InternalServerError(s"Cannot Update Gateway for deployment : $deployment"))
          )
      } ++ stableCluster.map(cluster ⇒ cluster.gateways.filterNot(routing ⇒ blueprintCluster.gateways.exists(_.port.name == routing.port.name))).getOrElse(Nil).map(Future.successful)
    }
  }

  private def resolveRoutings(deployment: Deployment, cluster: DeploymentCluster): List[Gateway] = {

    def routeBy(gateway: Gateway, service: DeploymentService, port: Port) = {
      gateway.routeBy(service.breed.name :: Nil).orElse(gateway.routeBy(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil))
    }

    val ports = cluster.services.flatMap(_.breed.ports).map(port ⇒ port.name → port).toMap.values.toList

    ports.map { port ⇒
      val services = cluster.services.filter(_.breed.ports.exists(_.name == port.name))

      cluster.gatewayBy(port.name) match {

        case Some(newRouting) ⇒
          val routes = services.map { service ⇒
            routeBy(newRouting, service, port) match {
              case None        ⇒ DefaultRoute("", RootAnyMap.empty, serviceRoutePath(deployment, cluster, service.breed.name, port.name), None, None, None, Nil, None)
              case Some(route) ⇒ route
            }
          }
          newRouting.copy(routes = routes, port = newRouting.port.copy(`type` = port.`type`))

        case None ⇒
          Gateway("", RootAnyMap.empty, Port(port.name, None, None, 0, port.`type`), None, None, Nil, services.map { service ⇒
            DefaultRoute("", RootAnyMap.empty, serviceRoutePath(deployment, cluster, service.breed.name, port.name), None, None, None, Nil, None)
          })
      }
    }
  }

  def processGateway(deployment: Deployment): Gateway ⇒ Gateway = { gateway ⇒
    val routes = gateway.routes.map {
      case route: DefaultRoute   ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route: RouteReference ⇒ route.copy(path = GatewayPath(deployment.name :: route.path.segments))
      case route                 ⇒ route
    }
    gateway.copy(name = Deployment.gatewayNameFor(deployment, gateway), routes = routes)
  }

  def resolveHosts(deployment: Deployment): Deployment = {
    deployment.copy(hosts = deployment.clusters.map(cluster ⇒ Host(TraitReference(cluster.name, TraitReference.Hosts, Host.host).toString, Some(DeploymentActor.gatewayHost()))))
  }

  def validateEmptyVariables(deployment: Deployment): UnitPlaceholder = {
    deployment.clusters.flatMap({ cluster ⇒
      cluster.services.flatMap(service ⇒ {
        service.breed.ports.filter(_.value.isEmpty).map(port ⇒ {
          val name = TraitReference(cluster.name, TraitReference.Ports, port.name).toString
          deployment.environmentVariables.find(_.name == name).getOrElse(throwException(UnresolvedVariableValueError(service.breed, port.name)))
        })

        service.breed.environmentVariables.filter(_.value.isEmpty).map(environmentVariable ⇒ {
          service.environmentVariables.find(_.name == environmentVariable.name).orElse {
            val name = TraitReference(cluster.name, TraitReference.EnvironmentVariables, environmentVariable.name).toString
            deployment.environmentVariables.find(_.name == name)
          } getOrElse throwException(UnresolvedVariableValueError(service.breed, environmentVariable.name))
        })
      })
    })
    UnitPlaceholder
  }

  def resolveDependencyMapping(deployment: Deployment): Deployment = {
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

trait DeploymentSlicer extends DeploymentGatewayOperation {
  this: DeploymentValidator with ArtifactSupport with CommonProvider ⇒

  def validateRoutingWeightOfServicesForRemoval(actualDeployment: Deployment, deploymentForBlueprint: Deployment) = actualDeployment.clusters.foreach { actualCluster ⇒
    deploymentForBlueprint.clusters.find(_.name == actualCluster.name).foreach { bpc ⇒
      val servicesNotFound = actualCluster.services.filterNot(service ⇒ bpc.services.exists(_.breed.name == service.breed.name))
      servicesNotFound.flatMap(_.breed.ports).map(_.name).toSet[String].foreach { port ⇒
        val weight = weightOf(actualCluster, servicesNotFound, port)
        if (weight != 100 && !(weight == 0 && servicesNotFound.isEmpty))
          throwException(InvalidRouteWeight(actualDeployment, actualCluster, port, weight))
      }
    }
  }

  def doSlice(blueprint: Deployment, stable: Deployment, validateOnly: Boolean): Future[Deployment] = {
    validateRoutingWeightOfServicesForRemoval(stable, blueprint)

    val newClusters = stable.clusters.map(cluster ⇒
      blueprint.clusters.find(_.name == cluster.name).map { bpc ⇒

        val services = cluster.services.map { service ⇒
          if (bpc.services.exists(service.breed.name == _.breed.name)) {
            service.copy(status = Intention.Undeployment)
          }
          else service
        }

        val routing = cluster.gateways.map { gateway ⇒
          updateRoutePaths(stable, cluster, gateway.copy(routes = gateway.routes.filterNot { route ⇒
            route.path.segments match {
              case _ :: _ :: s :: _ :: Nil ⇒ bpc.services.exists(_.breed.name == s)
              case _                       ⇒ false
            }
          }))
        } map (updateRoutePaths(stable, cluster, _))

        cluster.copy(services = services, gateways = routing)

      } getOrElse cluster)

    val deployment = (stable.copy(clusters = newClusters.filter(_.services.nonEmpty)))
    validateServices(deployment)
    validateInternalGateways(deployment)
    validateScaleEscalations(deployment)

    val (deleteRouting, updateRouting) = newClusters.partition(cluster ⇒ cluster.services.isEmpty || cluster.services.forall(_.status.intention == Intention.Undeployment))

    implicit val timeout = GatewayActor.timeout
    for {
      _ ← Future.sequence(stable.clusters.map(cluster ⇒
        (blueprint.clusters.find(_.name == cluster.name).map { bpc ⇒
          cluster.services.map { service ⇒
            if (bpc.services.exists(service.breed.name == _.breed.name)) {
              if (!validateOnly) Some(resetServiceArtifacts(stable, bpc, service, Intention.Undeployment)) else None
            }
            else None
          }.flatten
        }).getOrElse(Nil)
      ).flatten)

      _ ← Future.sequence {
        deleteRouting.flatMap { cluster ⇒
          stable.clusters.find(_.name == cluster.name) match {
            case Some(c) ⇒
              c.services.flatMap(_.breed.ports).map(_.name).distinct.map { portName ⇒
                actorFor[GatewayActor] ? GatewayActor.Delete(GatewayPath(deployment.name :: cluster.name :: portName :: Nil).normalized, validateOnly = validateOnly, force = true)
              }
            case None ⇒ List.empty[Future[_]]
          }
        } ++ updateRouting.flatMap { cluster ⇒
          stable.clusters.find(_.name == cluster.name) match {
            case Some(_) ⇒
              cluster.gateways.map(updateRoutePaths(stable, cluster, _)).map {
                gateway ⇒
                  if (gateway.routes.isEmpty)
                    actorFor[GatewayActor] ? GatewayActor.Delete(gateway.name, validateOnly = validateOnly, force = true)
                  else
                    actorFor[GatewayActor] ? GatewayActor.Update(gateway, None, validateOnly = validateOnly, promote = true, force = true)
              }
            case None ⇒ List.empty[Future[_]]
          }
        }
      }
    } yield deployment
  }
}
