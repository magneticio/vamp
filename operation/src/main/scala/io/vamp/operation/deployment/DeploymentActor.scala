package io.vamp.operation.deployment

import java.util.UUID

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.crypto.Hash
import io.vamp.common.notification.NotificationProvider
import io.vamp.dictionary.DictionaryActor
import io.vamp.model.artifact.DeploymentService.State
import io.vamp.model.artifact.DeploymentService.State.Intention._
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.{ BlueprintReader, BreedReader }
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.Synchronize
import io.vamp.operation.notification._
import io.vamp.persistence.{ ArtifactPaginationSupport, ArtifactSupport, PersistenceActor }

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }
import scala.util.matching.Regex

object DeploymentActor {

  trait DeploymentMessages

  case class Create(blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class Merge(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class Slice(name: String, blueprint: Blueprint, source: String, validateOnly: Boolean) extends DeploymentMessages

  case class UpdateSla(deployment: Deployment, cluster: DeploymentCluster, sla: Option[Sla], source: String) extends DeploymentMessages

  case class UpdateScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale, source: String) extends DeploymentMessages

  case class UpdateRouting(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, routing: DefaultRouting, source: String) extends DeploymentMessages

}

class DeploymentActor extends CommonSupportForActors with BlueprintSupport with DeploymentValidator with DeploymentMerger with DeploymentSlicer with DeploymentUpdate with ArtifactSupport with ArtifactPaginationSupport with OperationNotificationProvider {

  import DeploymentActor._

  def receive = {
    case Create(blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint)) andThen commit(create = true, source, validateOnly))(deploymentFor())
    }

    case Merge(name, blueprint, source, validateOnly) ⇒ reply {
      (merge(deploymentFor(blueprint)) andThen commit(create = true, source, validateOnly))(deploymentFor(name, create = true))
    }

    case Slice(name, blueprint, source, validateOnly) ⇒ reply {
      (slice(deploymentFor(blueprint)) andThen commit(create = false, source, validateOnly))(deploymentFor(name))
    }

    case UpdateSla(deployment, cluster, sla, source) ⇒ reply {
      updateSla(deployment, cluster, sla, source)
    }

    case UpdateScale(deployment, cluster, service, scale, source) ⇒ reply {
      updateScale(deployment, cluster, service, scale, source)
    }

    case UpdateRouting(deployment, cluster, service, routing, source) ⇒ reply {
      updateRouting(deployment, cluster, service, routing, source)
    }

    case any ⇒ unsupported(UnsupportedDeploymentRequest(any))
  }

  def commit(create: Boolean, source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Deployment]) = { (future: Future[Deployment]) ⇒
    if (validateOnly) future
    else {
      future.flatMap {
        case deployment ⇒
          implicit val timeout: Timeout = PersistenceActor.timeout
          checked[Deployment](IoC.actorFor[PersistenceActor] ? PersistenceActor.Update(deployment, Some(source), create = create)) map {
            case persisted ⇒
              IoC.actorFor[DeploymentSynchronizationActor] ! Synchronize(persisted)
              persisted
          }
      }
    }
  }
}

trait BlueprintSupport extends DeploymentValidator {
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
          Deployment(name, clusters = Nil, endpoints = Nil, ports = Nil, environmentVariables = Nil, hosts = Nil)
      }
    }
  }

  def deploymentFor(blueprint: Blueprint): Future[Deployment] = {
    artifactFor[DefaultBlueprint](blueprint) flatMap {
      case bp ⇒
        val clusters = bp.clusters.map { cluster ⇒
          Future.traverse(cluster.services)({ service ⇒
            for {
              breed ← artifactFor[DefaultBreed](service.breed)
              scale ← artifactFor[DefaultScale](service.scale)
              routing ← artifactFor[DefaultRouting](service.routing)
            } yield {
              DeploymentService(Deploy, breed, service.environmentVariables, scale, routing, Nil, Map(), service.dialects)
            }
          }).map(DeploymentCluster(cluster.name, _, cluster.sla, Map(), cluster.dialects))
        }
        Future.sequence(clusters).map(Deployment(uuid, _, bp.endpoints, Nil, bp.environmentVariables, Nil))
    }
  }
}

trait DeploymentValidator {

  this: ArtifactPaginationSupport with ArtifactSupport with ExecutionContextProvider with NotificationProvider ⇒

  private val nameMatcher = """^[\p{L}\d-]+$""".r

  def validateName(name: String) = name match {
    case nameMatcher(_*) ⇒
    case _               ⇒ throwException(IllegalDeploymentName(name))
  }

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

  def validateRoutingWeights: (Deployment ⇒ Deployment) = { (deployment: Deployment) ⇒
    deployment.clusters.map(cluster ⇒ cluster -> weightOf(cluster.services)).find({
      case (cluster, weight) ⇒ weight != 100 && weight != 0
    }).flatMap({
      case (cluster, weight) ⇒ throwException(UnsupportedRoutingWeight(deployment, cluster, weight))
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

  def validateBlueprintEndpoints: (Future[Deployment] ⇒ Future[Deployment]) = { (futureBlueprint: Future[Deployment]) ⇒
    // Reference check.
    futureBlueprint.map {
      case bp: AbstractBlueprint ⇒ bp.environmentVariables.find(ev ⇒ !traitExists(bp, TraitReference.referenceFor(ev.name), strictBreeds = true)).flatMap {
        case t ⇒ throwException(UnresolvedEndpointPortError(t.name, t.value))
      }.getOrElse(bp)
      case blueprint ⇒ blueprint
    }
  }

  def validateEndpoints: (Deployment ⇒ Future[Deployment]) = { (deployment: Deployment) ⇒
    // Availability check.
    implicit val timeout = PersistenceActor.timeout
    allArtifacts[Deployment] map {
      case deployments ⇒
        val ports = deployments.filterNot(_.name == deployment.name).flatMap { d ⇒
          d.endpoints.map(_.number -> d)
        }.toMap

        deployment.endpoints.foreach { port ⇒
          ports.get(port.number) match {
            case Some(d) ⇒ throwException(UnavailableEndpointPortError(port, d))
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

  def weightOf(services: List[DeploymentService]) = services.flatMap(_.routing).flatMap(_.weight).sum
}

trait DeploymentOperation {
  def commit(create: Boolean, source: String, validateOnly: Boolean): (Future[Deployment] ⇒ Future[Deployment])
}

trait DeploymentMerger extends DeploymentOperation with DeploymentTraitResolver {
  this: DeploymentValidator with ArtifactSupport with ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  def validateBlueprint = validateBlueprintEnvironmentVariables andThen validateBlueprintEndpoints

  def resolveProperties = resolveHosts andThen resolveRouteMapping andThen validateEmptyVariables andThen resolveDependencyMapping

  def validateMerge = validateServices andThen validateRoutingWeights andThen validateScaleEscalations andThen validateEndpoints

  def merge(blueprint: Future[Deployment]): (Future[Deployment] ⇒ Future[Deployment]) = { (futureDeployment: Future[Deployment]) ⇒
    futureDeployment.flatMap {
      case deployment ⇒
        (validateBlueprint andThen resolveProperties)(blueprint) flatMap {
          case attachment ⇒
            mergeClusters(futureDeployment, attachment) flatMap {
              case clusters ⇒
                val endpoints = mergeTrait(attachment.endpoints, deployment.endpoints)
                val ports = mergeTrait(attachment.ports, deployment.ports)
                val environmentVariables = mergeTrait(attachment.environmentVariables, deployment.environmentVariables)
                val hosts = mergeTrait(attachment.hosts, deployment.hosts)

                validateMerge(Deployment(deployment.name, clusters, endpoints, ports, environmentVariables, hosts))
            }
        }
    }
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
              case services ⇒ cluster.copy(services = services)
            }
            case Some(deploymentCluster) ⇒ mergeServices(stable, Some(deploymentCluster), cluster).map {
              case services ⇒ deploymentCluster.copy(services = services, routes = cluster.routes ++ deploymentCluster.routes, dialects = deploymentCluster.dialects ++ cluster.dialects, sla = cluster.sla)
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
            val routing = if (bpService.routing.isDefined) bpService.routing else service.routing
            val state: State = if (service.scale != bpService.scale || service.routing != bpService.routing) Deploy else service.state

            service.copy(scale = scale, routing = routing, state = state, dialects = service.dialects ++ bpService.dialects)
        }
      }
    }
  }

  def mergeNewServices(deployment: Deployment, stableCluster: Option[DeploymentCluster], blueprintCluster: DeploymentCluster): List[Future[DeploymentService]] = {
    val newServices = blueprintCluster.services.filter(service ⇒ stableCluster match {
      case None     ⇒ true
      case Some(sc) ⇒ !sc.services.exists(_.breed.name == service.breed.name)
    })

    if (newServices.nonEmpty) {
      val oldWeight = stableCluster.flatMap(cluster ⇒ Some(cluster.services.flatMap({ service ⇒
        blueprintCluster.services.find(_.breed.name == service.breed.name) match {
          case None         ⇒ service.routing
          case Some(update) ⇒ update.routing
        }
      }).flatMap(_.weight).sum)) match {
        case None      ⇒ 0
        case Some(sum) ⇒ sum
      }

      val newWeight = newServices.flatMap(_.routing).filter(_.isInstanceOf[DefaultRouting]).flatMap(_.weight).sum
      val availableWeight = 100 - oldWeight - newWeight

      if (availableWeight < 0)
        throwException(RoutingWeightError(blueprintCluster))

      val weight = Math.round(availableWeight / newServices.size)

      newServices.view.zipWithIndex.toList.map {
        case (service, index) ⇒
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
            case scale ⇒
              val defaultWeight = if (index == newServices.size - 1) availableWeight - index * weight else weight
              val routing = service.routing match {
                case None                    ⇒ Some(DefaultRouting("", Some(defaultWeight), Nil))
                case Some(r: DefaultRouting) ⇒ Some(r.copy(weight = Some(r.weight.getOrElse(defaultWeight))))
              }
              service.copy(scale = Some(scale), routing = routing)
          }
      }
    } else Nil
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
    futureDeployment.flatMap {
      case deployment ⇒
        Future.sequence(deployment.clusters.map({ cluster ⇒
          Future.sequence(cluster.services.map(_.breed).flatMap(_.ports).map(_.number).map(port ⇒ cluster.routes.get(port) match {
            case None ⇒
              implicit val timeout = DictionaryActor.timeout
              val key = DictionaryActor.portAssignment.format(deployment.name, port)
              actorFor[DictionaryActor] ? DictionaryActor.Get(key) map {
                case number: Int ⇒ port -> number
                case e           ⇒ throwException(UnresolvedEnvironmentValueError(key, e))
              }
            case Some(number) ⇒ Future(port -> number)
          })).map(routes ⇒ cluster.copy(routes = routes.toMap))
        })).map {
          case clusters ⇒ deployment.copy(clusters = clusters)
        }
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
      val weight = weightOf(cluster.services.filterNot(service ⇒ bpc.services.exists(_.breed.name == service.breed.name)))
      if (weight != 100 && weight != 0) throwException(InvalidRoutingWeight(deployment, cluster, weight))
    }
  }

  def slice(futureBlueprint: Future[Deployment]): (Future[Deployment] ⇒ Future[Deployment]) = { (stableFuture: Future[Deployment]) ⇒
    for {
      blueprint ← futureBlueprint
      stable ← stableFuture
    } yield {
      validateRoutingWeightOfServicesForRemoval(stable, blueprint)

      (validateServices andThen validateRoutingWeights andThen validateScaleEscalations)(stable.copy(clusters = stable.clusters.map(cluster ⇒
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

  def updateRouting(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, routing: DefaultRouting, source: String) = {
    lazy val services = cluster.services.map(s ⇒ if (s.breed.name == service.breed.name) service.copy(routing = Some(routing), state = Deploy) else s)
    val clusters = deployment.clusters.map(c ⇒ if (c.name == cluster.name) c.copy(services = services) else c)
    actorFor[PersistenceActor] ? PersistenceActor.Update(validateRoutingWeights(deployment.copy(clusters = clusters)), Some(source))
  }
}
