package io.vamp.persistence.jdbc

import com.typesafe.scalalogging.Logger
import io.vamp.model.artifact._
import io.vamp.persistence.notification.{ ArtifactNotFound, PersistenceNotificationProvider }
import io.vamp.persistence.slick.model._
import org.slf4j.LoggerFactory

import scala.slick.jdbc.JdbcBackend

trait DeploymentStore extends BlueprintStore with BreedStore with EnvironmentVariableStore with ScaleStore with RoutingStore with SlaStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  private val logger = Logger(LoggerFactory.getLogger(classOf[DeploymentStore]))

  import io.vamp.persistence.slick.components.Components.instance._
  import io.vamp.persistence.slick.model.Implicits._

  protected def updateDeployment(existing: DeploymentModel, artifact: Deployment): Unit = {
    deleteChildren(existing)
    createChildren(artifact, existing.id.get)
    Deployments.update(existing)
  }

  private def deleteDeploymentClusters(clusters: List[DeploymentClusterModel], deploymentId: Option[Int]): Unit = {
    for (cluster ← clusters) {
      for (route ← cluster.routes) {
        ClusterRoutes.deleteById(route.id.get)
      }
      for (service ← cluster.services) {
        deleteEnvironmentVariables(service.environmentVariables)
        for (dependency ← service.dependencies) {
          DeploymentServiceDependencies.deleteById(dependency.id.get)
        }
        for (server ← service.servers) {
          for (port ← server.ports) {
            ServerPorts.deleteById(port.id.get)
          }
          DeploymentServers.deleteById(server.id.get)
        }
        DeploymentServices.deleteById(service.id.get)
        BreedReferences.findOptionById(service.breed) match {
          case Some(breedRef) ⇒
            if (breedRef.isDefinedInline)
              DefaultBreeds.findOptionByName(breedRef.name, service.deploymentId) match {
                case Some(breed) ⇒ deleteDefaultBreedModel(breed)
                case None        ⇒ logger.debug(s"Referenced breed ${breedRef.name} not found")
              }
            BreedReferences.deleteById(breedRef.id.get)
          case None ⇒ // Nothing to delete
        }
        service.scale match {
          case Some(scaleRefId) ⇒
            ScaleReferences.findOptionById(scaleRefId) match {
              case Some(scaleRef) if scaleRef.isDefinedInline ⇒
                DefaultScales.findOptionByName(scaleRef.name, service.deploymentId) match {
                  case Some(scale) ⇒ DefaultScales.deleteById(scale.id.get)
                  case None        ⇒ // Should not happen (log it as not critical)
                }
                ScaleReferences.deleteById(scaleRefId)
              case Some(scaleRef) ⇒
                ScaleReferences.deleteById(scaleRefId)
              case None ⇒ logger.warn(s"Referenced scale not found.")
            }
          case None ⇒ // Nothing to delete
        }
        service.routing match {
          case Some(routingId) ⇒
            RoutingReferences.findOptionById(routingId) match {
              case Some(routingRef) if routingRef.isDefinedInline ⇒
                DefaultRoutings.findOptionByName(routingRef.name, service.deploymentId) match {
                  case Some(routing) ⇒ deleteRoutingModel(routing)
                  case None          ⇒ logger.debug(s"Referenced routing ${routingRef.name} not found")
                }
                RoutingReferences.deleteById(routingRef.id.get)
              case Some(routingRef) ⇒
                RoutingReferences.deleteById(routingRef.id.get)
              case None ⇒ logger.warn(s"Referenced routing not found.")
            }
          case None ⇒ // Nothing to delete
        }
      }
      DeploymentClusters.deleteById(cluster.id.get)

      cluster.slaReference match {
        case Some(slaRef) ⇒
          SlaReferences.findOptionById(slaRef) match {
            case Some(slaReference) ⇒
              for (escalationReference ← slaReference.escalationReferences) {
                GenericEscalations.findOptionByName(escalationReference.name, deploymentId) match {
                  case Some(escalation) if escalation.isAnonymous ⇒ deleteEscalationModel(escalation)
                }
                EscalationReferences.deleteById(escalationReference.id.get)
              }
              GenericSlas.findOptionByName(slaReference.name, slaReference.deploymentId) match {
                case Some(sla) ⇒ deleteSlaModel(sla)
                case None      ⇒ logger.debug(s"Referenced sla ${slaReference.name} not found")
              }
            case None ⇒
          }
          SlaReferences.deleteById(slaRef)
        case None ⇒ // Nothing to delete
      }
    }
  }

  private def createDeploymentClusters(clusters: List[DeploymentCluster], deploymentId: Option[Int]): Unit = {
    for (cluster ← clusters) {
      val slaRefId = createSla(cluster.sla, deploymentId)
      val clusterId = DeploymentClusters.add(DeploymentClusterModel(name = cluster.name, slaReference = slaRefId, deploymentId = deploymentId, dialects = DialectSerializer.serialize(cluster.dialects)))
      for (route ← cluster.routes) {
        ClusterRoutes.add(ClusterRouteModel(portIn = route._1, portOut = route._2, clusterId = clusterId))
      }
      for (service ← cluster.services) {
        val breedRefId = createBreedReference(service.breed, deploymentId)
        val message = service.state.step match {
          case failure: DeploymentService.State.Step.Failure ⇒
            Some(s"Problem in cluster ${cluster.name}, with a service containing breed ${BreedReferences.findById(breedRefId).name}.")
          case _ ⇒ None
        }
        val serviceId = DeploymentServices.add(
          DeploymentServiceModel(
            clusterId = clusterId,
            name = s"${cluster.name}~${java.util.UUID.randomUUID.toString}", // Dummy name
            deploymentId = deploymentId,
            breed = breedRefId,
            scale = createScaleReference(service.scale, deploymentId),
            routing = createRoutingReference(service.routing, deploymentId),
            deploymentIntention = service.state.intention,
            deploymentStep = service.state.step,
            deploymentTime = service.state.since,
            deploymentStepTime = service.state.step.since,
            dialects = DialectSerializer.serialize(service.dialects),
            message = message)
        )
        createEnvironmentVariables(service.environmentVariables, EnvironmentVariableParentType.Service, serviceId, deploymentId)
        for (dep ← service.dependencies) DeploymentServiceDependencies.add(DeploymentServiceDependencyModel(name = dep._1, value = dep._2, serviceId = serviceId))
        for (server ← service.instances) {
          val serverId = DeploymentServers.add(DeploymentServerModel(serviceId = serviceId, name = server.name, host = server.host, deployed = server.deployed, deploymentId = deploymentId))
          for (port ← server.ports) ServerPorts.add(ServerPortModel(portIn = port._1, portOut = port._2, serverId = serverId))
        }
      }
    }
  }

  protected def findDeploymentOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] =
    Deployments.findOptionByName(name) flatMap { deployment ⇒
      Some(
        Deployment(
          name = deployment.name,
          clusters = findDeploymentClusterArtifacts(deployment.clusters, deployment.id),
          endpoints = readPortsToArtifactList(deployment.endpoints),
          environmentVariables = deployment.environmentVariables.map(e ⇒ environmentVariableModel2Artifact(e)),
          hosts = deployment.hosts.map(h ⇒ hostModel2Artifact(h)),
          ports = readPortsToArtifactList(deployment.ports)
        )
      )
    }

  private def findDeploymentClusterArtifacts(clusters: List[DeploymentClusterModel], deploymentId: Option[Int]): List[DeploymentCluster] =
    clusters.map(cluster ⇒
      DeploymentCluster(
        name = cluster.name,
        services = findDeploymentServiceArtifacts(cluster.services),
        routes = clusterRouteModels2Artifacts(cluster.routes),
        sla = findOptionSlaArtifactViaReferenceId(cluster.slaReference, deploymentId),
        dialects = DialectSerializer.deserialize(cluster.dialects)
      )
    )

  private def clusterRouteModels2Artifacts(routes: List[ClusterRouteModel]): Map[Int, Int] =
    routes.map(route ⇒ route.portIn -> route.portOut).toMap

  private def findDeploymentServiceArtifacts(services: List[DeploymentServiceModel]): List[DeploymentService] =
    services.map(service ⇒
      DeploymentService(state = deploymentService2deploymentState(service),
        breed = defaultBreedModel2DefaultBreedArtifact(DefaultBreeds.findByName(BreedReferences.findById(service.breed).name, service.deploymentId)),
        environmentVariables = service.environmentVariables.map(e ⇒ environmentVariableModel2Artifact(e)),
        scale = service.scale flatMap { scale ⇒ Some(defaultScaleModel2Artifact(DefaultScales.findByName(ScaleReferences.findById(scale).name, service.deploymentId))) },
        routing = service.routing flatMap { routing ⇒ Some(defaultRoutingModel2Artifact(DefaultRoutings.findByName(RoutingReferences.findById(routing).name, service.deploymentId))) },
        instances = deploymentServerModels2Artifacts(service.servers),
        dependencies = deploymentServiceDependencies2Artifacts(service.dependencies),
        dialects = DialectSerializer.deserialize(service.dialects)
      )
    )

  private def deploymentServerModels2Artifacts(servers: List[DeploymentServerModel]): List[DeploymentInstance] =
    servers.map(server ⇒
      DeploymentInstance(name = server.name, host = server.host, ports = serverPorts2Artifact(server.ports), deployed = server.deployed)
    )

  private def serverPorts2Artifact(ports: List[ServerPortModel]): Map[Int, Int] = ports.map(port ⇒ port.portIn -> port.portOut).toMap

  private def deploymentServiceDependencies2Artifacts(dependencies: List[DeploymentServiceDependencyModel]): Map[String, String] =
    dependencies.map(dep ⇒ dep.name -> dep.value).toMap

  protected def deleteDeploymentFromDb(artifact: Deployment): Unit = {
    Deployments.findOptionByName(artifact.name) match {
      case Some(deployment) ⇒ deleteDeploymentModel(deployment)
      case None             ⇒ throwException(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  private def deleteDeploymentModel(m: DeploymentModel): Unit = {
    deleteChildren(m)
    Deployments.deleteById(m.id.get)
  }

  protected def createDeploymentArtifact(a: Deployment): String = {
    val deploymentId = Deployments.add(a)
    createChildren(a, deploymentId = deploymentId)
    Deployments.findById(deploymentId).name
  }

  private def deleteChildren(model: DeploymentModel): Unit = {
    deleteDeploymentClusters(model.clusters, model.id)
    deleteModelPorts(model.endpoints)
    deleteEnvironmentVariables(model.environmentVariables)
    deleteConstants(model.constants)
    for (host ← model.hosts) DeploymentHosts.deleteById(host.id.get)
    deleteModelPorts(model.ports)
  }

  private def createChildren(deployment: Deployment, deploymentId: Int): Unit = {
    createDeploymentClusters(deployment.clusters, Some(deploymentId))
    createPorts(deployment.endpoints, Some(deploymentId), Some(PortParentType.DeploymentEndPoint))
    createEnvironmentVariables(deployment.environmentVariables, EnvironmentVariableParentType.Deployment, deploymentId, Some(deploymentId))
    for (host ← deployment.hosts) DeploymentHosts.add(HostModel(name = host.name, value = host.value, deploymentId = Some(deploymentId)))
    createPorts(deployment.ports, Some(deploymentId), Some(PortParentType.DeploymentPort))
  }

}
